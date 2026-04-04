import { BrowserRouter as Router, Routes, Route, Navigate } from "react-router-dom";
import { useScrollToTop } from "./hooks/useScrollToTop";
import Home from "./pages/Home";
import FAQ from "./pages/FAQ";
import Blog from "./pages/Blog";
import BlogPost from "./pages/BlogPost";
import Roadmap from "./pages/Roadmap";
import Community from "./pages/Community";

function AppRoutes() {
  useScrollToTop();

  return (
    <Routes>
      <Route path="/" element={<Home />} />
      <Route path="/faq" element={<FAQ />} />
      <Route path="/blog" element={<Blog />} />
      <Route path="/blog/:id" element={<BlogPost />} />
      <Route path="/roadmap" element={<Roadmap />} />
      <Route path="/community" element={<Community />} />
      <Route path="*" element={<Navigate to="/" replace />} />
    </Routes>
  );
}

export default function App() {
  return (
    <Router>
      <AppRoutes />
    </Router>
  );
}
