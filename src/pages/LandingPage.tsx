import { Link } from "react-router-dom";
import Footer from "../components/Footer";
import Navbar from "../components/Navbar";
import { SEO } from "../components/SEO";
import { useTheme } from "../lib/ThemeContext";
import { landingPageByPath, type LandingPageData } from "../data/landingPages";
import { APP_ID, PLAY_STORE_URL, absoluteUrl, breadcrumbSchema, faqSchema, ORGANIZATION_ID, WEBSITE_ID } from "../seo/site";

const shortTitle = (page: LandingPageData) => page.title.replace(/\s*\|\s*PocketHost$/, "");

export default function LandingPage({ path }: { path: string }) {
  const { theme } = useTheme();
  const page = landingPageByPath(path);
  if (!page) throw new Error(`No landing page data for ${path}`);

  const dark = theme === "dark";
  const text = dark ? "text-white" : "text-black";
  const muted = dark ? "text-white/65" : "text-black/70";
  const card = dark ? "border-white/10 bg-white/[0.02]" : "border-black/10 bg-white";

  return (
    <div className={`min-h-screen section-transition ${dark ? "bg-[#0a0a0a] text-white" : "bg-white text-black"}`}>
      <SEO
        title={page.title}
        description={page.description}
        path={page.path}
        schema={[
          {
            "@type": "WebPage",
            "@id": `${absoluteUrl(page.path)}#webpage`,
            url: absoluteUrl(page.path),
            name: shortTitle(page),
            description: page.description,
            isPartOf: { "@id": WEBSITE_ID },
            publisher: { "@id": ORGANIZATION_ID },
            about: { "@id": APP_ID },
          },
          faqSchema(page.faqs),
          breadcrumbSchema([{ name: page.h1, path: page.path }]),
        ]}
      />
      <Navbar />

      <section className={`border-b-4 py-20 px-6 ${dark ? "border-white/5" : "border-black/5"}`}>
        <div className="max-w-3xl mx-auto text-center">
          <h1 className="text-4xl md:text-5xl font-extrabold mb-6">{page.h1}</h1>
          <p className={`text-lg leading-relaxed mb-8 ${muted}`}>{page.intro}</p>
          <div className="flex flex-wrap gap-4 justify-center">
            <a href={PLAY_STORE_URL} target="_blank" rel="noopener noreferrer" className="btn-duo px-10 py-4 inline-block">
              Get PocketHost free
            </a>
            <Link to="/" className={`px-10 py-4 inline-block font-bold border-2 rounded-lg ${card} ${text}`}>
              See how it works
            </Link>
          </div>
        </div>
      </section>

      <main className="py-16 px-6">
        <div className="max-w-3xl mx-auto space-y-12">
          {page.sections.map((section) => (
            <section key={section.heading}>
              <h2 className="text-2xl md:text-3xl font-extrabold mb-4">{section.heading}</h2>
              {section.paragraphs?.map((paragraph) => (
                <p key={paragraph} className={`leading-relaxed mb-4 ${muted}`}>
                  {paragraph}
                </p>
              ))}
              {section.bullets && (
                <ul className={`list-disc pl-6 space-y-2 leading-relaxed ${muted}`}>
                  {section.bullets.map((bullet) => (
                    <li key={bullet}>{bullet}</li>
                  ))}
                </ul>
              )}
            </section>
          ))}

          <section>
            <h2 className="text-2xl md:text-3xl font-extrabold mb-4">Frequently asked questions</h2>
            <div className="space-y-4">
              {page.faqs.map((faq) => (
                <div key={faq.question} className={`border-2 rounded-lg p-5 ${card}`}>
                  <h3 className="text-lg font-bold mb-2">{faq.question}</h3>
                  <p className={`leading-relaxed ${muted}`}>{faq.answer}</p>
                </div>
              ))}
            </div>
          </section>

          <section>
            <h2 className="text-2xl md:text-3xl font-extrabold mb-4">Keep reading</h2>
            <ul className="space-y-2">
              {page.related.map((relatedPath) => {
                const related = landingPageByPath(relatedPath);
                return related ? (
                  <li key={relatedPath}>
                    <Link to={relatedPath} className="text-[#7FE620] font-bold hover:underline">
                      {related.h1}
                    </Link>
                  </li>
                ) : null;
              })}
              <li>
                <Link to="/faq" className="text-[#7FE620] font-bold hover:underline">
                  PocketHost FAQ
                </Link>
              </li>
            </ul>
          </section>
        </div>
      </main>

      <Footer />
    </div>
  );
}
