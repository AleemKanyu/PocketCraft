import { BrowserRouter as Router, Routes, Route, useParams, Navigate } from "react-router-dom";
import Home from "./pages/Home";
import Admin from "./pages/Admin";
import Leaderboard from "./pages/Leaderboard";
import { useEffect } from "react";

function RefRedirect() {
  const { code } = useParams();
  
  useEffect(() => {
    if (code) {
      localStorage.setItem("pocketcraft_ref", code);
    }
  }, [code]);

  return <Navigate to="/" replace />;
}

export default function App() {
  return (
    <Router>
      <Routes>
        <Route path="/" element={<Home />} />
        <Route path="/leaderboard" element={<Leaderboard />} />
        <Route path="/ref/:code" element={<RefRedirect />} />
        <Route path="/admin" element={<Admin />} />
      </Routes>
    </Router>
  );
}
