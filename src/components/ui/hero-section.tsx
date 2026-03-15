import React from 'react';
import { motion } from 'framer-motion';
import { ArrowRight } from 'lucide-react';
import { cn } from '../../lib/utils';
import { Button } from './button';

export interface FinancialHeroProps {
  title: React.ReactNode;
  description: string;
  buttonText: string;
  buttonLink: string;
  imageUrl1: string;
  imageUrl2: string;
  className?: string;
}

const containerVariants = {
  hidden: { opacity: 0 },
  visible: {
    opacity: 1,
    transition: { staggerChildren: 0.15 },
  },
};

const itemVariants = {
  hidden: { opacity: 0, y: 16 },
  visible: {
    opacity: 1,
    y: 0,
    transition: { duration: 0.45 },
  },
};

const cardsVariants = {
  hidden: { opacity: 0, x: 40 },
  visible: {
    opacity: 1,
    x: 0,
    transition: { duration: 0.7, ease: 'easeOut', staggerChildren: 0.25 },
  },
};

const cardItemVariants = {
  hidden: { opacity: 0, x: 30 },
  visible: { opacity: 1, x: 0 },
};

export const FinancialHero = ({
  title,
  description,
  buttonText,
  buttonLink,
  imageUrl1,
  imageUrl2,
  className,
}: FinancialHeroProps) => {
  return (
    <section
      className={cn(
        'relative w-full overflow-hidden bg-[#080808] text-white',
        className
      )}
    >
      {/* Subtle dot grid */}
      <div className="absolute inset-0 opacity-[0.04]"
        style={{
          backgroundImage: 'radial-gradient(circle, #4CAF50 1px, transparent 1px)',
          backgroundSize: '2rem 2rem',
        }}
      />
      {/* Subtle glow */}
      <div className="absolute top-0 left-1/2 -translate-x-1/2 w-[600px] h-[300px] bg-[#4CAF50]/5 blur-[120px] rounded-full pointer-events-none" />

      <motion.div
        className="relative container mx-auto flex min-h-[88vh] items-center justify-between px-4 sm:px-6 pt-24 pb-16 lg:flex-row flex-col gap-10 lg:gap-16"
        initial="hidden"
        animate="visible"
        variants={containerVariants}
      >
        {/* Left: Text */}
        <div className="flex flex-col items-center text-center lg:items-start lg:text-left lg:w-1/2 w-full">
          <motion.div variants={itemVariants} className="mb-6">
            <span className="inline-block px-3 py-1 rounded-full text-xs font-semibold bg-[#4CAF50]/10 text-[#4CAF50] border border-[#4CAF50]/20 tracking-wider uppercase">
              Coming Soon
            </span>
          </motion.div>

          <motion.h1
            className="text-3xl sm:text-4xl md:text-5xl lg:text-6xl font-bold tracking-tight leading-[1.1] text-white"
            variants={itemVariants}
          >
            {title}
          </motion.h1>
          <motion.p
            className="mt-5 max-w-md text-sm sm:text-base text-white/40 leading-relaxed"
            variants={itemVariants}
          >
            {description}
          </motion.p>
          <motion.div variants={itemVariants} className="mt-8">
            <a
              href={buttonLink}
              onClick={(e) => {
                if (buttonLink.startsWith('#')) {
                  e.preventDefault();
                  document.getElementById(buttonLink.slice(1))?.scrollIntoView({ behavior: 'smooth' });
                }
              }}
            >
              <Button
                size="lg"
                className="h-11 px-6 text-sm font-semibold bg-[#4CAF50] hover:bg-[#43A047] text-black border-0 rounded-full shadow-none transition-colors"
              >
                {buttonText}
                <ArrowRight className="ml-2 h-4 w-4" />
              </Button>
            </a>
          </motion.div>
        </div>

        {/* Right: Cards */}
        <motion.div
          className="relative lg:w-1/2 w-full flex items-center justify-center min-h-[280px] sm:min-h-[380px]"
          variants={cardsVariants}
        >
          <motion.img
            src={imageUrl2}
            alt="PocketCraft Background"
            variants={cardItemVariants}
            whileHover={{ y: -8, rotate: -4, transition: { duration: 0.3 } }}
            className="absolute h-40 sm:h-60 md:h-72 rounded-2xl shadow-2xl object-cover transform rotate-[-6deg] translate-x-20 sm:translate-x-28 border-2 border-white/5"
          />
          <motion.img
            src={imageUrl1}
            alt="PocketCraft Foreground"
            variants={cardItemVariants}
            whileHover={{ y: -8, rotate: 4, transition: { duration: 0.3 } }}
            className="absolute h-40 sm:h-60 md:h-72 rounded-2xl shadow-2xl object-cover transform rotate-[6deg] -translate-x-12 sm:-translate-x-20 border-2 border-white/5"
          />
        </motion.div>
      </motion.div>
    </section>
  );
};
