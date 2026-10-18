/**
 * Real 5-star reviews from PocketHost's Google Play listing, copied word for word (spelling
 * included). "…" marks a part left out; the only parts left out compare us to other hosts.
 * Source: https://play.google.com/store/apps/details?id=com.pockethost.app (India listing),
 * read 18 Oct 2026. Do not add reviews that are not on the listing.
 */
export interface Review {
  name: string;
  date: string;
  text: string;
}

export const PLAY_REVIEWS_URL =
  "https://play.google.com/store/apps/details?id=com.pockethost.app&hl=en_IN&gl=IN&showAllReviews=true";

export const reviews: Review[] = [
  {
    name: "Kartik singh",
    date: "12 Oct 2026",
    text:
      "best minecraft hosting app ever, literally they are so kind … and they actually respond to us in time, the servers stability is also pretty good, It is also COMPLETELY FREE … Overall a good support system and a REALLY Good hosting app, I would definitely recommend",
  },
  {
    name: "Sairaj Vaje",
    date: "17 Sep 2026",
    text:
      "Loved The App,Community Is Pretty Amazing ❤️They Actively Fix Bugs.Best App I Could Ever Expect Looking Forward To Your Upcoming Projects As Well.",
  },
  {
    name: "Rayan Rastogi",
    date: "29 Sep 2026",
    text:
      "The best server hosting app ever ther are no ads and no bloatt too and a it's a seamless experience and easy to use …",
  },
  {
    name: "Ayman Bin Nafih",
    date: "16 Oct 2026",
    text: "It an absolutely goated app, and it works very good and is easy to setup",
  },
  {
    name: "Fe2baeknaeh E",
    date: "7 Oct 2026",
    text: "Fast support, great server host, and lovely staffs. Hopefully they add a Philippine region next!",
  },
  {
    name: "bhalchandra_ patil",
    date: "14 Oct 2026",
    text: "thanks pockethost I am soo glad that you gave me opportunity to host a free server thanksss",
  },
  {
    name: "Vip Gamer",
    date: "14 Oct 2026",
    text: "best hosting ever I seen 💖💖💖💖",
  },
  {
    name: "Pradyumn Pratap Singh",
    date: "13 Oct 2026",
    text: "Beauty at its peak",
  },
  {
    name: "Mita Tandel",
    date: "13 Oct 2026",
    text: "very useful and nice app",
  },
  {
    name: "Debanjan Mondal",
    date: "13 Oct 2026",
    text: "5 star full support",
  },
];
