import { motion } from "framer-motion";
import { Star } from "lucide-react";
import { useTheme } from "../lib/ThemeContext";
import { useLowEndDevice } from "../hooks/useLowEndDevice";
import { reviews, PLAY_REVIEWS_URL, type Review } from "../data/reviews";

const Stars = () => (
  <div className="flex gap-0.5" aria-label="5 out of 5 stars">
    {Array.from({ length: 5 }).map((_, i) => (
      <Star key={i} className="w-4 h-4 fill-[#FFC800] text-[#FFC800]" aria-hidden="true" />
    ))}
  </div>
);

const ReviewCard = ({ review, isDark }: { review: Review; isDark: boolean }) => (
  <figure
    className={`w-[300px] md:w-[340px] shrink-0 rounded-2xl border-2 border-b-4 p-5 flex flex-col gap-3 ${
      isDark ? "border-white/10 bg-white/[0.03]" : "border-black/10 bg-white"
    }`}
  >
    <Stars />
    <blockquote
      className={`text-sm md:text-[15px] leading-relaxed flex-1 ${isDark ? "text-white/80" : "text-black/75"}`}
    >
      “{review.text}”
    </blockquote>
    <figcaption className="flex items-center gap-3 pt-1">
      <div className="w-9 h-9 rounded-full bg-[#7FE620]/20 text-[#58A700] font-extrabold flex items-center justify-center text-sm">
        {review.name.trim().charAt(0).toUpperCase()}
      </div>
      <div className="min-w-0">
        <div className={`font-bold text-sm truncate ${isDark ? "text-white" : "text-black"}`}>{review.name}</div>
        <div className={`text-xs ${isDark ? "text-white/40" : "text-black/45"}`}>Google Play · {review.date}</div>
      </div>
    </figcaption>
  </figure>
);

/** One row that glides sideways forever; the list is doubled so the loop has no seam. */
const MarqueeRow = ({ items, reverse, isDark }: { items: Review[]; reverse?: boolean; isDark: boolean }) => (
  <div className="reviews-marquee group overflow-hidden">
    <div
      className={`flex w-max gap-4 py-2 ${reverse ? "reviews-track-reverse" : "reviews-track"} group-hover:[animation-play-state:paused]`}
    >
      {[...items, ...items].map((review, i) => (
        <div key={`${review.name}-${i}`} aria-hidden={i >= items.length ? true : undefined}>
          <ReviewCard review={review} isDark={isDark} />
        </div>
      ))}
    </div>
  </div>
);

const Reviews = () => {
  const { theme } = useTheme();
  const isDark = theme === "dark";
  const isLowEnd = useLowEndDevice();
  const half = Math.ceil(reviews.length / 2);

  return (
    <section
      id="reviews"
      className={`py-24 border-t-4 section-transition overflow-hidden ${isDark ? "border-white/5" : "border-black/5"}`}
    >
      <div className="max-w-4xl mx-auto px-6 text-center mb-12">
        <motion.h2
          initial={{ opacity: 0, y: 16 }}
          whileInView={{ opacity: 1, y: 0 }}
          viewport={{ once: true }}
          transition={{ duration: 0.45 }}
          className={`text-3xl md:text-5xl font-extrabold mb-4 ${isDark ? "text-white" : "text-black"}`}
        >
          What our players say
        </motion.h2>
        <p className={`${isDark ? "text-white/50" : "text-black/60"}`}>
          Real reviews from PocketHost players on Google Play.{" "}
          <a
            href={PLAY_REVIEWS_URL}
            target="_blank"
            rel="noopener noreferrer"
            className={`font-bold hover:underline ${isDark ? "text-[#7FE620]" : "text-[#58A700]"}`}
          >
            Read them all
          </a>
        </p>
      </div>

      {isLowEnd ? (
        <div className="max-w-6xl mx-auto px-6 flex flex-wrap justify-center gap-4">
          {reviews.map((review) => (
            <ReviewCard key={review.name} review={review} isDark={isDark} />
          ))}
        </div>
      ) : (
        <div className="flex flex-col gap-4">
          <MarqueeRow items={reviews.slice(0, half)} isDark={isDark} />
          <MarqueeRow items={reviews.slice(half)} reverse isDark={isDark} />
        </div>
      )}
    </section>
  );
};

export default Reviews;
