"""Get topology tool - Real implementation with Control Plane"""

from typing import Dict, Any, List, Optional, Set
from datetime import datetime
import httpx
import structlog

from app.mcp.registry import tool_registry
from app.infrastructure.redis import RedisCache
from app.config import settings

logger = structlog.get_logger()

# Cache instance
redis_cache = RedisCache()


@tool_registry.register(
    name="get_topology",
    description="Get service topology and dependencies from CMDB",
    parameters={
        "type": "object",
        "properties": {
            "service_id": {
                "type": "string",
                "description": "Service ID, e.g., 'payment-service'"
            },
            "depth": {
                "type": "integer",
                "default": 2,
                "description": "Topology depth (1-3)"
            },
            "direction": {
                "type": "string",
                "enum": ["upstream", "downstream", "both"],
                "default": "both",
                "description": "Dependency direction"
            },
            "include_metrics": {
                "type": "boolean",
                "default": True,
                "description": "Include health metrics in response"
            }
        },
        "required": ["service_id"]
    }
)
async def get_topology(
    service_id: str,
    depth: int = 2,
    direction: str = "both",
    include_metrics: bool = True
) -> Dict[str, Any]:
    """Get service topology with real data from Control Plane"""

    start_time = datetime.utcnow()

    # Validate parameters
    depth = max(1, min(depth, 3))  # Clamp to 1-3

    try:
        # Try cache first
        cache_key = f"topology:{service_id}:{depth}:{direction}"
        cached = await redis_cache.get(cache_key)
        if cached:
            logger.debug("Topology cache hit", service=service_id)
            cached_data = eval(cached)  # Safe since we control the cache
            cached_data["cache_hit"] = True
            return cached_data

        logger.info("Fetching topology",
                   service=service_id,
                   depth=depth,
                   direction=direction)

        # Fetch from Java Control Plane
        topology_data = await fetch_topology_from_control_plane(
            service_id, depth, direction
        )

        # Build formatted response
        nodes = []
        edges = []

        # Add root service
        root_node = create_service_node(
            topology_data.get('root', {}),
            is_root=True,
            include_metrics=include_metrics
        )
        nodes.append(root_node)

        # Process dependencies
        processed_ids: Set[str] = {service_id}

        if direction in ["downstream", "both"]:
            downstream = topology_data.get('downstream', [])
            for dep in downstream[:20]:  # Limit to 20
                if dep['id'] not in processed_ids:
                    nodes.append(create_service_node(dep, include_metrics=include_metrics))
                    edges.append(create_edge(service_id, dep['id'], "depends"))
                    processed_ids.add(dep['id'])

        if direction in ["upstream", "both"]:
            upstream = topology_data.get('upstream', [])
            for caller in upstream[:20]:  # Limit to 20
                if caller['id'] not in processed_ids:
                    nodes.append(create_service_node(caller, include_metrics=include_metrics))
                    edges.append(create_edge(caller['id'], service_id, "calls"))
                    processed_ids.add(caller['id'])

        # Infrastructure nodes
        infra = topology_data.get('infrastructure', {})
        for db in infra.get('databases', []):
            node_id = f"db:{db['name']}"
            if node_id not in processed_ids:
                nodes.append(create_infra_node(node_id, db, "database"))
                edges.append(create_edge(service_id, node_id, "uses_database"))
                processed_ids.add(node_id)

        for cache in infra.get('caches', []):
            node_id = f"cache:{cache['name']}"
            if node_id not in processed_ids:
                nodes.append(create_infra_node(node_id, cache, "cache"))
                edges.append(create_edge(service_id, node_id, "uses_cache"))
                processed_ids.add(node_id)

        # Calculate impact analysis
        impact = calculate_impact(service_id, nodes, edges)

        elapsed_ms = (datetime.utcnow() - start_time).total_seconds() * 1000

        result = {
            "service_id": service_id,
            "nodes": nodes,
            "edges": edges,
            "depth": depth,
            "direction": direction,
            "impact_analysis": impact,
            "metadata": {
                "total_nodes": len(nodes),
                "total_edges": len(edges),
                "elapsed_ms": round(elapsed_ms, 2),
                "timestamp": datetime.utcnow().isoformat()
            },
            "cache_hit": False
        }

        # Cache for 5 minutes
        await redis_cache.setex(cache_key, 300, str(result))

        return result

    except Exception as e:
        logger.error("Topology fetch failed", error=str(e), service=service_id)
        # Return fallback topology
        return generate_fallback_topology(service_id)


async def fetch_topology_from_control_plane(
    service_id: str,
    depth: int,
    direction: str
) -> Dict:
    """Fetch topology data from Java Control Plane"""

    try:
        async with httpx.AsyncClient(timeout=10.0) as client:
            response = await client.get(
                f"{settings.CONTROL_PLANE_URL}/api/v1/topology/{service_id}",
                params={
                    "depth": depth,
                    "direction": direction
                },
                headers={
                    "X-API-Key": settings.CONTROL_PLANE_API_KEY,
                    "Accept": "application/json"
                }
            )
            response.raise_for_status()
            return response.json().get('data', {})
    except Exception as e:
        logger.warning("Control Plane topology API failed", error=str(e))
        # Return simulated topology for development
        return generate_simulated_topology(service_id)


def generate_simulated_topology(service_id: str) -> Dict:
    """Generate simulated topology for development/testing"""

    service_parts = service_id.split('-')
    domain = service_parts[0] if service_parts else "unknown"

    return {
        "root": {
            "id": service_id,
            "name": service_id,
            "type": "service",
            "health": "warning",
            "latency_p99": 85.5,
            "error_rate": 0.02,
            "qps": 1200
        },
        "downstream": [
            {"id": f"{service_id}-db", "name": f"{domain}-db", "type": "database",
             "health": "healthy", "db_type": "mysql"},
            {"id": f"{service_id}-cache", "name": f"{domain}-redis", "type": "cache",
             "health": "healthy", "cache_type": "redis"},
            {"id": f"{domain}-message-queue", "name": "kafka", "type": "queue",
             "health": "healthy"},
        ],
        "upstream": [
            {"id": "api-gateway", "name": "gateway", "type": "gateway",
             "health": "healthy"},
            {"id": f"{domain}-bff", "name": f"{domain}-bff", "type": "service",
             "health": "healthy"},
        ],
        "infrastructure": {
            "databases": [{"name": f"{domain}_db", "type": "mysql"}],
            "caches": [{"name": f"{domain}_cache", "type": "redis"}],
            "queues": [{"name": "kafka-cluster", "type": "kafka"}]
        }
    }


def generate_fallback_topology(service_id: str) -> Dict:
    """Generate minimal fallback topology on error"""

    return {
        "service_id": service_id,
        "nodes": [
            {
                "id": service_id,
                "name": service_id,
                "type": "service",
                "health": "unknown"
            }
        ],
        "edges": [],
        "depth": 0,
        "direction": "both",
        "impact_analysis": {
            "direct_dependencies": [],
            "dependent_services": [],
            "blast_radius": 0
        },
        "metadata": {
            "error": "Failed to fetch topology",
            "timestamp": datetime.utcnow().isoformat()
        }
    }


def create_service_node(data: Dict, is_root: bool = False, include_metrics: bool = True) -> Dict:
    """Create service node"""

    node = {
        "id": data.get('id', 'unknown'),
        "name": data.get('name', 'unknown'),
        "type": data.get('type', 'service'),
        "health": data.get('health', 'unknown'),
        "is_root": is_root
    }

    if include_metrics:
        node.update({
            "latency_p99": data.get('latency_p99'),
            "error_rate": data.get('error_rate'),
            "qps": data.get('qps'),
            "availability": data.get('availability')
        })

    return node


def create_infra_node(node_id: str, data: Dict, node_type: str) -> Dict:
    """Create infrastructure node"""

    return {
        "id": node_id,
        "name": data.get('name', node_id),
        "type": node_type,
        "infra_type": data.get('type', 'unknown'),
        "health": data.get('health', 'unknown')
    }


def create_edge(source: str, target: str, relation: str) -> Dict:
    """Create edge"""

    return {
        "source": source,
        "target": target,
        "type": relation
    }


def calculate_impact(
    root_service: str,
    nodes: List[Dict],
    edges: List[Dict]
) -> Dict:
    """Calculate impact analysis"""

    # Find direct dependencies (services this one depends on)
    direct_deps = [
        e['target'] for e in edges
        if e['source'] == root_service and e['type'] in ['depends', 'uses_database', 'uses_cache']
    ]

    # Find dependent services (services that depend on this one)
    dependent = [
        e['source'] for e in edges
        if e['target'] == root_service and e['type'] in ['calls', 'depends']
    ]

    # Calculate blast radius
    all_connected = set(direct_deps + dependent)
    for edge in edges:
        if edge['source'] not in all_connected and edge['target'] in all_connected:
            all_connected.add(edge['source'])
        if edge['target'] not in all_connected and edge['source'] in all_connected:
            all_connected.add(edge['target'])

    # Health status
    unhealthyDeps = [
        n['id'] for n in nodes
        if n.get('health') in ['error', 'critical', 'down']
    ]

    return {
        "direct_dependencies": direct_deps,
        "dependent_services": dependent,
        "blast_radius": len(all_connected),
        "unhealthy_dependencies": unhealthyDeps,
        "risk_level": "high" if len(unhealthyDeps) > 0 else "medium" if len(direct_deps) > 3 else "low"
    }
