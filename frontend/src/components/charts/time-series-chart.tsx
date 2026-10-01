"use client";

import { useLayoutEffect, useMemo, useRef, useState, type PointerEvent } from "react";

export interface TimePoint {
  time: number;
  value: number | null;
}

interface TimeSeriesChartProps {
  /** Accessible name; the visible title is rendered by the surrounding card. */
  label: string;
  points: TimePoint[];
  formatValue: (value: number) => string;
  height?: number;
  /** Fixed upper bound (e.g. 100 for percentages); otherwise derived from the data. */
  maxValue?: number;
  /** Smallest upper bound when derived from the data, so near-zero series stay readable. */
  minTop?: number;
}

const PADDING = { top: 8, right: 8, bottom: 20, left: 48 };

/**
 * Single-series line chart: one axis, recessive grid, 2px line, gaps for missing samples, and a
 * crosshair tooltip that snaps to the nearest sample. A visually hidden table carries the same
 * values for screen readers.
 */
export function TimeSeriesChart({
  label,
  points,
  formatValue,
  height = 160,
  maxValue,
  minTop = 0,
}: TimeSeriesChartProps) {
  const container = useRef<HTMLDivElement>(null);
  const [width, setWidth] = useState(0);
  const [hover, setHover] = useState<number | null>(null);

  useLayoutEffect(() => {
    const element = container.current;
    if (!element) {
      return;
    }
    const observer = new ResizeObserver(([entry]) => setWidth(entry.contentRect.width));
    observer.observe(element);
    return () => observer.disconnect();
  }, []);

  const scale = useMemo(() => {
    const values = points.map((p) => p.value).filter((v): v is number => v !== null);
    const top = maxValue ?? Math.max(niceCeiling(Math.max(...values, 0)), minTop);
    const start = points[0]?.time ?? 0;
    const end = points[points.length - 1]?.time ?? start + 1;
    const plotWidth = Math.max(width - PADDING.left - PADDING.right, 1);
    const plotHeight = height - PADDING.top - PADDING.bottom;
    return {
      top,
      x: (time: number) => PADDING.left + ((time - start) / Math.max(end - start, 1)) * plotWidth,
      y: (value: number) => PADDING.top + plotHeight - (value / (top || 1)) * plotHeight,
      plotHeight,
    };
  }, [points, width, height, maxValue, minTop]);

  const path = useMemo(() => {
    let d = "";
    let drawing = false;
    for (const point of points) {
      if (point.value === null) {
        drawing = false;
        continue;
      }
      d += `${drawing ? "L" : "M"}${scale.x(point.time).toFixed(1)},${scale.y(point.value).toFixed(1)}`;
      drawing = true;
    }
    return d;
  }, [points, scale]);

  function handlePointer(event: PointerEvent<SVGRectElement>) {
    const bounds = event.currentTarget.getBoundingClientRect();
    const x = event.clientX - bounds.left + PADDING.left;
    let nearest = 0;
    points.forEach((point, index) => {
      if (Math.abs(scale.x(point.time) - x) < Math.abs(scale.x(points[nearest].time) - x)) {
        nearest = index;
      }
    });
    setHover(nearest);
  }

  const ticks = [0, scale.top / 2, scale.top];
  const hovered = hover === null ? null : points[hover];

  if (points.length < 2) {
    return (
      <div
        className="flex items-center justify-center text-sm text-muted-foreground"
        style={{ height }}
      >
        Collecting data…
      </div>
    );
  }

  return (
    <div ref={container} className="relative">
      <svg width={width} height={height} role="img" aria-label={label} className="block">
        {ticks.map((tick) => (
          <g key={tick}>
            <line
              x1={PADDING.left}
              x2={width - PADDING.right}
              y1={scale.y(tick)}
              y2={scale.y(tick)}
              className="stroke-border"
              strokeWidth={1}
            />
            <text
              x={PADDING.left - 6}
              y={scale.y(tick)}
              textAnchor="end"
              dominantBaseline="middle"
              className="fill-muted-foreground text-[10px]"
            >
              {formatValue(tick)}
            </text>
          </g>
        ))}
        {[points[0], points[points.length - 1]].map((point, index) => (
          <text
            key={index}
            x={scale.x(point.time)}
            y={height - 4}
            textAnchor={index === 0 ? "start" : "end"}
            className="fill-muted-foreground text-[10px]"
          >
            {new Date(point.time).toLocaleTimeString([], { hour: "2-digit", minute: "2-digit" })}
          </text>
        ))}
        <path
          d={path}
          fill="none"
          stroke="var(--viz-series-1)"
          strokeWidth={2}
          strokeLinejoin="round"
        />
        {hovered && (
          <g pointerEvents="none">
            <line
              x1={scale.x(hovered.time)}
              x2={scale.x(hovered.time)}
              y1={PADDING.top}
              y2={PADDING.top + scale.plotHeight}
              className="stroke-muted-foreground"
              strokeWidth={1}
            />
            {hovered.value !== null && (
              <circle
                cx={scale.x(hovered.time)}
                cy={scale.y(hovered.value)}
                r={4}
                fill="var(--viz-series-1)"
                className="stroke-card"
                strokeWidth={2}
              />
            )}
          </g>
        )}
        <rect
          x={PADDING.left}
          y={PADDING.top}
          width={Math.max(width - PADDING.left - PADDING.right, 0)}
          height={scale.plotHeight}
          fill="transparent"
          onPointerMove={handlePointer}
          onPointerLeave={() => setHover(null)}
        />
      </svg>
      {hovered && (
        <div
          className="pointer-events-none absolute top-0 rounded-md border bg-popover px-2 py-1 text-xs shadow-sm"
          style={{
            left: Math.min(scale.x(hovered.time) + 8, Math.max(width - 120, 0)),
          }}
        >
          <div className="font-semibold text-foreground">
            {hovered.value === null ? "no data" : formatValue(hovered.value)}
          </div>
          <div className="text-muted-foreground">{new Date(hovered.time).toLocaleTimeString()}</div>
        </div>
      )}
      <table className="sr-only">
        <caption>{label}</caption>
        <tbody>
          {points.map((point) => (
            <tr key={point.time}>
              <td>{new Date(point.time).toLocaleTimeString()}</td>
              <td>{point.value === null ? "no data" : formatValue(point.value)}</td>
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  );
}

/** Rounds up to 1, 2, or 5 × a power of ten so axis labels stay readable. */
export function niceCeiling(value: number): number {
  if (value <= 0) {
    return 1;
  }
  const magnitude = 10 ** Math.floor(Math.log10(value));
  const normalized = value / magnitude;
  const step = normalized <= 1 ? 1 : normalized <= 2 ? 2 : normalized <= 5 ? 5 : 10;
  return step * magnitude;
}
