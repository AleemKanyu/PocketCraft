import { useEffect } from "react";
import { useLocation } from "react-router-dom";

export const useScrollToTop = () => {
  const { pathname, hash } = useLocation();

  useEffect(() => {
    if (hash) {
      const targetId = hash.replace("#", "");
      const scrollToTarget = () => {
        const element = document.getElementById(targetId);
        if (element) {
          element.scrollIntoView({ behavior: "smooth" });
          return true;
        }
        return false;
      };

      // Lazy-loaded pages can take a moment to render the target, so keep trying briefly.
      if (!scrollToTarget()) {
        let tries = 0;
        const timer = setInterval(() => {
          tries += 1;
          if (scrollToTarget() || tries >= 20) clearInterval(timer);
        }, 100);
        return () => clearInterval(timer);
      }
    } else {
      window.scrollTo(0, 0);
    }
  }, [pathname, hash]);
};
