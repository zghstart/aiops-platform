import { createBrowserRouter } from 'react-router-dom'
import { Dashboard } from './Dashboard'
import { Layout } from '../components/Layout'
import { AlertDetail } from './AlertDetail'
import { IncidentList } from './IncidentList'
import { IncidentDetail } from './IncidentDetail'
import { TopologyPage } from './TopologyPage'

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
    ],
  },
])
