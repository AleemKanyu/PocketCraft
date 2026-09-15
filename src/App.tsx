import { lazy, Suspense } from "react";
import { BrowserRouter as Router, Routes, Route, Navigate } from "react-router-dom";
import { useScrollToTop } from "./hooks/useScrollToTop";
import Home from "./pages/Home";
import { ThemeProvider, useTheme } from "./lib/ThemeContext";
import { CubicPixelTrail } from "./components/CubicPixelTrail";

const FAQ = lazy(() => import("./pages/FAQ"));
const Blog = lazy(() => import("./pages/Blog"));
const BlogPost = lazy(() => import("./pages/BlogPost"));
const Roadmap = lazy(() => import("./pages/Roadmap"));
const Community = lazy(() => import("./pages/Community"));
const Privacy = lazy(() => import("./pages/Privacy"));
const Terms = lazy(() => import("./pages/Terms"));

const RouteLoadingFallback = () => (
  <div className="min-h-screen bg-[#0a0a0a] flex items-center justify-center">
    <div className="w-8 h-8 rounded-full border-2 border-[#7FE620] border-t-transparent animate-spin" />
  </div>
);

function AppRoutes() {
  useScrollToTop();

  return (
    <Suspense fallback={<RouteLoadingFallback />}>
      <Routes>
        <Route path="/" element={<Home />} />
        <Route path="/faq" element={<FAQ />} />
        <Route path="/blog" element={<Blog />} />
        <Route path="/blog/:id" element={<BlogPost />} />
        <Route path="/roadmap" element={<Roadmap />} />
        <Route path="/community" element={<Community />} />
        <Route path="/privacy" element={<Privacy />} />
        <Route path="/terms" element={<Terms />} />
        <Route path="*" element={<Navigate to="/" replace />} />
      </Routes>
    </Suspense>
  );
}

function MainLayout() {
  const { theme } = useTheme();
  return (
    <div className={`relative isolate min-h-screen select-none ${theme === "dark" ? "bg-[#0a0a0a]" : "bg-white"}`}>
      <CubicPixelTrail />
      <Router>
        <AppRoutes />
      </Router>
    </div>
  );
}

export default function App() {
  return (
    <ThemeProvider>
      <MainLayout />
    </ThemeProvider>
  );
}
