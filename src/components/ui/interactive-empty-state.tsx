import React, { memo, useId, forwardRef } from 'react';
import { motion, LazyMotion, domAnimation } from 'framer-motion';
import { clsx, type ClassValue } from "clsx";
import { twMerge } from "tailwind-merge";

export function cn(...inputs: ClassValue[]) {
  return twMerge(clsx(inputs));
}

const ICON_VARIANTS = {
  left: {
    initial: { scale: 0.8, opacity: 0, x: 0, y: 0, rotate: 0 },
    animate: { scale: 1, opacity: 1, x: 0, y: 0, rotate: -6, transition: { duration: 0.4, delay: 0.1 } },
    hover: { x: -22, y: -5, rotate: -15, scale: 1.1, transition: { duration: 0.2 } }
  },
  center: {
    initial: { scale: 0.8, opacity: 0 },
    animate: { scale: 1, opacity: 1, transition: { duration: 0.4, delay: 0.2 } },
    hover: { y: -10, scale: 1.15, transition: { duration: 0.2 } }
  },
  right: {
    initial: { scale: 0.8, opacity: 0, x: 0, y: 0, rotate: 0 },
    animate: { scale: 1, opacity: 1, x: 0, y: 0, rotate: 6, transition: { duration: 0.4, delay: 0.3 } },
    hover: { x: 22, y: -5, rotate: 15, scale: 1.1, transition: { duration: 0.2 } }
  }
};

const CONTENT_VARIANTS = {
  initial: { y: 20, opacity: 0 },
  animate: { y: 0, opacity: 1, transition: { duration: 0.4, delay: 0.2 } },
};

const BUTTON_VARIANTS = {
  initial: { y: 20, opacity: 0 },
  animate: { y: 0, opacity: 1, transition: { duration: 0.4, delay: 0.3 } },
};

const IconContainer = memo(({ children, variant, className = '', theme }: any) => (
  <motion.div
    variants={ICON_VARIANTS[variant as keyof typeof ICON_VARIANTS]}
    className={cn(
      "w-12 h-12 flex items-center justify-center relative transition-all duration-300",
      theme === 'dark' && "bg-[#1a1f1a] border-[#333] border-2 rounded-2xl",
      theme === 'sleek' && "bg-[#1a1f1a]/80 backdrop-blur-md border border-[#4CAF50]/20 rounded-2xl shadow-lg shadow-[#4CAF50]/5",
      (!theme || theme === 'light') && "bg-white border border-stone-200/60 shadow-sm group-hover:shadow-md group-hover:border-stone-300 rounded-2xl",
      className
    )}
  >
    <div className={cn(
      "text-sm transition-colors duration-300",
      theme === 'dark' && "text-[#A0A0A0] group-hover:text-white",
      theme === 'sleek' && "text-[#81C784] group-hover:text-[#4CAF50]",
      (!theme || theme === 'light') && "text-stone-500 group-hover:text-stone-700"
    )}>
      {children}
    </div>
  </motion.div>
));
IconContainer.displayName = "IconContainer";

const MultiIconDisplay = memo(({ icons, theme }: any) => {
  if (!icons || icons.length < 3) return null;

  return (
    <div className="flex justify-center isolate relative">
      <IconContainer variant="left" className="left-2 top-1 z-10" theme={theme}>
        {icons[0]}
      </IconContainer>
      <IconContainer variant="center" className="z-20" theme={theme}>
        {icons[1]}
      </IconContainer>
      <IconContainer variant="right" className="right-2 top-1 z-10" theme={theme}>
        {icons[2]}
      </IconContainer>
    </div>
  );
});
MultiIconDisplay.displayName = "MultiIconDisplay";

const Background = ({ theme }: any) => (
  <div
    aria-hidden="true"
    className="absolute inset-0 opacity-0 group-hover:opacity-[0.03] transition-opacity duration-500 pointer-events-none"
    style={{
      backgroundImage: `radial-gradient(circle at 2px 2px, #000 1px, transparent 1px)`,
      backgroundSize: '24px 24px'
    }}
  />
);

export const EmptyState = forwardRef(({
  title,
  description,
  icons,
  action,
  variant = 'default',
  size = 'default',
  theme = 'light',
  isIconAnimated = true,
  className = '',
  ...props
}: any, ref: any) => {
  const titleId = useId();
  const descriptionId = useId();

  const baseClasses = "group transition-all duration-500 relative overflow-hidden text-center flex flex-col items-center justify-center";

  const sizeClasses: any = {
    sm: "p-6",
    default: "p-8",
    lg: "p-12"
  };

  const getVariantClasses = (variant: string, theme: string) => {
    const variants: any = {
      default: {
        light: "bg-white border border-stone-200/60 shadow-sm hover:shadow-md hover:border-stone-300/80 rounded-[2rem]",
        dark: "bg-[#1a1f1a] border border-[#333] hover:bg-[#222922] rounded-[2rem]",
        sleek: "bg-[#111311]/50 backdrop-blur-xl border border-[#4CAF50]/10 hover:border-[#4CAF50]/30 hover:bg-[#1a1f1a]/80 hover:shadow-[0_8px_32px_rgba(76,175,80,0.1)] rounded-[2rem]"
      },
      subtle: {
        light: "bg-stone-50/50 border border-transparent hover:bg-stone-50 rounded-[2rem]",
        dark: "bg-[#111] border border-[#222] hover:bg-[#1a1f1a] rounded-[2rem]",
        sleek: "bg-[#111311] border border-[#4CAF50]/5 hover:bg-[#1a1f1a]/50 rounded-[2rem]"
      },
      error: {
        light: "bg-white border border-red-100 bg-red-50/30 hover:bg-red-50/50 rounded-[2rem]",
        dark: "bg-[#2a0000] border border-[#550000] hover:bg-[#3a0000] rounded-[2rem]",
        sleek: "bg-red-900/10 border border-red-500/20 hover:bg-red-900/20 rounded-[2rem]"
      }
    };
    return variants[variant][theme];
  };

  const getTextClasses = (type: string, size: string, theme: string) => {
    const sizes: any = {
      title: {
        sm: "text-base",
        default: "text-lg",
        lg: "text-xl"
      },
      description: {
        sm: "text-sm",
        default: "text-base",
        lg: "text-lg"
      }
    };

    const colors: any = {
      title: {
        light: "text-stone-900 font-medium tracking-tight",
        dark: "text-white font-medium",
        sleek: "text-[#e8f5e9] font-semibold tracking-tight"
      },
      description: {
        light: "text-stone-500",
        dark: "text-[#A0A0A0]",
        sleek: "text-[#a5d6a7]"
      }
    };

    return cn(sizes[type][size], colors[type][theme], "transition-colors duration-200");
  };

  const getButtonClasses = (size: string, theme: string) => {
    const sizeClasses: any = {
      sm: "text-xs px-4 py-2",
      default: "text-sm px-5 py-2.5",
      lg: "text-base px-6 py-3"
    };

    const themeClasses: any = {
      light: "bg-stone-900 hover:bg-stone-800 text-white rounded-full shadow-sm hover:shadow-md",
      dark: "border border-[#333] bg-[#222] hover:bg-[#333] text-white rounded-full",
      sleek: "bg-[#4CAF50] hover:bg-[#66BB6A] text-[#111] font-semibold rounded-full shadow-md shadow-[#4CAF50]/10"
    };

    return cn(
      "inline-flex items-center gap-2 font-medium transition-all duration-300 relative overflow-hidden group/button disabled:opacity-50 disabled:cursor-not-allowed",
      sizeClasses[size],
      themeClasses[theme]
    );
  };

  return (
    <LazyMotion features={domAnimation}>
      <motion.section
        ref={ref}
        role="region"
        aria-labelledby={titleId}
        aria-describedby={descriptionId}
        className={cn(
          baseClasses,
          sizeClasses[size],
          getVariantClasses(variant, theme),
          className
        )}
        initial="initial"
        animate="animate"
        whileHover={isIconAnimated ? "hover" : "animate"}
        {...props}
      >
        <Background theme={theme} />
        <div className="relative z-10 flex flex-col items-center">
          {icons && (
            <div className="mb-6">
              <MultiIconDisplay icons={icons} theme={theme} />
            </div>
          )}

          <motion.div variants={CONTENT_VARIANTS} className="space-y-2 mb-6">
            <h2 id={titleId} className={getTextClasses('title', size, theme)}>
              {title}
            </h2>
            {description && (
              <p
                id={descriptionId}
                className={cn(
                  getTextClasses('description', size, theme),
                  "max-w-md leading-relaxed"
                )}
              >
                {description}
              </p>
            )}
          </motion.div>

          {action && (
            <motion.div variants={BUTTON_VARIANTS}>
              <motion.button
                type="button"
                onClick={action.onClick}
                disabled={action.disabled}
                className={getButtonClasses(size, theme)}
                whileTap={{ scale: 0.97 }}
              >
                {action.icon && (
                  <motion.div
                    className="transition-transform duration-300 group-hover/button:translate-x-0.5"
                  >
                    {action.icon}
                  </motion.div>
                )}
                <span className="relative z-10">{action.label}</span>
              </motion.button>
            </motion.div>
          )}
        </div>
      </motion.section>
    </LazyMotion>
  );
});
EmptyState.displayName = "EmptyState";
