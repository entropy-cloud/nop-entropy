import { getSharedModule } from './_registry.js';

const router = getSharedModule('react-router-dom');

export const BrowserRouter = router.BrowserRouter;
export const HashRouter = router.HashRouter;
export const Link = router.Link;
export const MemoryRouter = router.MemoryRouter;
export const NavLink = router.NavLink;
export const Navigate = router.Navigate;
export const Outlet = router.Outlet;
export const Route = router.Route;
export const RouterProvider = router.RouterProvider;
export const Routes = router.Routes;
export const createBrowserRouter = router.createBrowserRouter;
export const createHashRouter = router.createHashRouter;
export const useLocation = router.useLocation;
export const useNavigate = router.useNavigate;
export const useParams = router.useParams;
export const useSearchParams = router.useSearchParams;
