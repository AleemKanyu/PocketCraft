import React from 'react';
import { cn } from '@/src/lib/utils';

export function PatternText({
	text = 'Text',
	className,
	...props
}: Omit<React.ComponentProps<'p'>, 'children'> & { text: string }) {
	return (
		<p
			data-shadow={text}
			className={cn(
				'relative inline-block text-[5em] md:text-[8em] font-minecraft',
				'[text-shadow:4px_4px_0_rgba(8,8,8,1)]',
				'after:absolute after:top-1 after:left-1 after:-z-1 after:content-[attr(data-shadow)]',
				'after:bg-size-[0.05em_0.05em] after:bg-clip-text after:text-transparent after:text-shadow-none',
				'after:bg-[linear-gradient(45deg,transparent_45%,#4CAF50_45%,#4CAF50_55%,transparent_0)]',
				'after:animate-[shadanim_15s_linear_infinite]',
				className,
			)}
			{...props}
		>
			{text}
		</p>
	);
}
