import { FC, useEffect, useRef } from "react";

export const MarqueeText: FC<{ text: string }> = ({ text }) => {
  const boxRef = useRef<HTMLDivElement>(null);
  const txtRef = useRef<HTMLSpanElement>(null);

  useEffect(() => {
    const box = boxRef.current;
    const txt = txtRef.current;
    if (!box || !txt) return;
    const amount = txt.scrollWidth - box.clientWidth;
    if (amount <= 2) return;
    const anim = txt.animate(
      [
        { transform: "translateX(0)", offset: 0 },
        { transform: "translateX(0)", offset: 0.15 },
        { transform: `translateX(-${amount}px)`, offset: 0.5 },
        { transform: `translateX(-${amount}px)`, offset: 0.65 },
        { transform: "translateX(0)", offset: 1 },
      ],
      { duration: Math.max(4000, amount * 55 + 2500), iterations: Infinity, easing: "ease-in-out" },
    );
    return () => anim.cancel();
  }, [text]);

  return (
    <div ref={boxRef} style={{ flex: 1, minWidth: 0, overflow: "hidden" }}>
      <span ref={txtRef} style={{ display: "inline-block", whiteSpace: "nowrap" }}>
        {text}
      </span>
    </div>
  );
};
