import { createBrowserRouter } from 'react-router-dom'
import { Dashboard } from '../pages/Dashboard'
import { Layout } from '../components/Layout'
import { AlertDetail } from '../pages/AlertDetail'
import { IncidentList } from '../pages/IncidentList'
import { IncidentDetail } from '../pages/IncidentDetail'
import { TopologyPage } from '../pages/TopologyPage'
// import { MonitoringIntegration } from '../pages/MonitoringIntegration'

export const router = createBrowserRouter([
  {
    path: '/',
    element: <Layout />,
    children: [
      {
        index: true,
        element: <Dashboard />,
      },
      {
        path: 'alerts/:alertId',
        element: <AlertDetail />,
      },
      {
        path: 'incidents',
        element: <IncidentList />,
      },
      {
        path: 'incidents/:incidentId',
        element: <IncidentDetail />,
      },
      {
        path: 'topology',
        element: <TopologyPage />,
      },
      // {
      //   path: 'monitoring',
      //   element: <MonitoringIntegration />,
      // },
    ],
  },
])
