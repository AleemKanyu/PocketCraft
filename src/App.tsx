import { lazy, Suspense } from "react";
import { Routes, Route } from "react-router-dom";
import { useScrollToTop } from "./hooks/useScrollToTop";
import Home from "./pages/Home";
import NotFound from "./pages/NotFound";
import { ThemeProvider, useTheme } from "./lib/ThemeContext";
import { CubicPixelTrail } from "./components/CubicPixelTrail";

const FAQ = lazy(() => import("./pages/FAQ"));
const Blog = lazy(() => import("./pages/Blog"));
const BlogPost = lazy(() => import("./pages/BlogPost"));
const Roadmap = lazy(() => import("./pages/Roadmap"));
const Community = lazy(() => import("./pages/Community"));
const Privacy = lazy(() => import("./pages/Privacy"));
const Terms = lazy(() => import("./pages/Terms"));
const DeleteAccount = lazy(() => import("./pages/DeleteAccount"));
const RemoteControlPage = lazy(() => import("./pages/RemoteControlPage"));

const RouteLoadingFallback = () => (
  <div className="min-h-screen bg-[#0a0a0a] flex items-center justify-center">
    <div className="w-8 h-8 rounded-full border-2 border-[#7FE620] border-t-transparent animate-spin" />
  </div>
);

// The single list of pages. The build also reads it (entry-server.tsx) to decide
// which URLs become static HTML and go into the sitemap, so a page added here is
// picked up everywhere.
export const pageRoutes = [
  { path: "/", element: <Home /> },
  { path: "/faq", element: <FAQ /> },
  { path: "/blog", element: <Blog /> },
  { path: "/blog/:id", element: <BlogPost /> },
  { path: "/roadmap", element: <Roadmap /> },
  { path: "/community", element: <Community /> },
  { path: "/privacy", element: <Privacy /> },
  { path: "/terms", element: <Terms /> },
  { path: "/delete-account", element: <DeleteAccount /> },
  { path: "/remote", element: <RemoteControlPage /> },
];

function AppRoutes() {
  useScrollToTop();

  return (
    <Suspense fallback={<RouteLoadingFallback />}>
      <Routes>
        {pageRoutes.map((route) => (
          <Route key={route.path} path={route.path} element={route.element} />
        ))}
        <Route path="*" element={<NotFound />} />
      </Routes>
    </Suspense>
  );
}

function MainLayout() {
  const { theme } = useTheme();
  return (
    <div className={`relative isolate min-h-screen select-none ${theme === "dark" ? "bg-[#0a0a0a]" : "bg-white"}`}>
      <CubicPixelTrail />
      <AppRoutes />
    </div>
  );
}

// The router is supplied by the caller: BrowserRouter in the browser (main.tsx),
// StaticRouter when pages are rendered to HTML at build time (entry-server.tsx).
export default function App() {
  return (
    <ThemeProvider>
      <MainLayout />
    </ThemeProvider>
  );
}
