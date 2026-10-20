// The existing Library mark is shared by every Jellyfin presentation.
export default function JellyfinLogo({ size = 20, className, label }) {
  return <svg data-jellyfin-logo viewBox="0 0 64 64" width={size} height={size} className={className} role={label ? 'img' : undefined} aria-label={label} aria-hidden={label ? undefined : true} fill="none" stroke="currentColor" strokeWidth="3"><path d="M28 8Q32 2 36 8L60 50Q64 58 55 58H9Q0 58 4 50Z"/><path d="M30 25Q32 21 34 25L47 47H17Z"/><path d="M32 34L38 44H26Z"/></svg>;
}
