// Dot-matrix skeletons (replace spinners on lists).
export const PosterGridSkeleton = ({ count = 6, className = "grid grid-cols-3 gap-x-3 gap-y-5 sm:grid-cols-4 lg:grid-cols-6" }) => (
  <div aria-busy="true" aria-label="Loading" className={className}>
    {Array.from({ length: count }, (_, i) => (
      <div key={i}>
        <div className="dot-skel aspect-[2/3] rounded-2xl" style={{ animationDelay: `${i * 80}ms` }} />
        <div className="dot-skel mt-2 h-3 w-3/4 rounded-full" />
        <div className="dot-skel mt-1.5 h-2.5 w-1/3 rounded-full" />
      </div>
    ))}
  </div>
);

export const ListSkeleton = ({ rows = 5 }) => (
  <div aria-busy="true" aria-label="Loading" className="space-y-2.5">
    {Array.from({ length: rows }, (_, i) => (
      <div key={i} className="n-card p-3" style={{ opacity: 1 - i * 0.12 }}>
        <div className="dot-skel h-3.5 w-4/5 rounded-full" style={{ animationDelay: `${i * 90}ms` }} />
        <div className="mt-3 flex gap-1.5">
          <div className="dot-skel h-6 w-14 rounded-full" />
          <div className="dot-skel h-6 w-16 rounded-full" />
          <div className="dot-skel h-6 w-12 rounded-full" />
        </div>
      </div>
    ))}
  </div>
);

export const RowSkeleton = ({ count = 4 }) => (
  <div className="flex gap-3 overflow-hidden" aria-busy="true">
    {Array.from({ length: count }, (_, i) => (
      <div key={i} className="dot-skel aspect-[2/3] w-[118px] shrink-0 rounded-2xl" style={{ animationDelay: `${i * 90}ms` }} />
    ))}
  </div>
);
