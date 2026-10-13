import { useEffect } from "react";
import { applyHead, buildHead, collectServerHead, type SEOProps } from "../seo/head";

export function SEO(props: SEOProps) {
  const head = buildHead(props);
  const serialized = JSON.stringify(head);

  if (typeof window === "undefined") {
    collectServerHead(head);
  }

  useEffect(() => {
    applyHead(JSON.parse(serialized));
  }, [serialized]);

  return null;
}
