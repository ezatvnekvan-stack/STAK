import { LayoutGrid, List, Search, SlidersHorizontal, X } from 'lucide-react';
import { Sheet, SheetContent, SheetHeader, SheetTitle, SheetDescription } from './ui/sheet';
import {AnimatedSelection} from './design/MotionSystem';

export function CollectionSearch({value,onChange,label,onFilters,count=0,testId}) {
  return <div className="collection-search-row"><div className="collection-search"><Search size={18} aria-hidden="true"/><input type="search" aria-label={label} placeholder={label} data-testid={testId} value={value} onChange={e=>onChange(e.target.value)}/>{value && <button type="button" aria-label={`Clear ${label.toLowerCase()}`} onClick={()=>onChange('')}><X size={16}/></button>}</div><button type="button" className="n-icon-btn collection-filter-button" aria-label="Filters and sorting" aria-haspopup="dialog" onClick={onFilters}><SlidersHorizontal size={18}/>{count>0 && <span className="collection-filter-dot" aria-label={`${count} active filters`}/>}</button></div>;
}

export function CollectionLayout({value,onChange}) {
  return <AnimatedSelection value={value} className="collection-layout" role="group" aria-label="Collection layout">{[['grid','Poster grid',LayoutGrid],['list','List view',List]].map(([id,label,Icon])=><button key={id} data-value={id} type="button" aria-label={label} aria-pressed={value===id} onClick={()=>onChange(id)}><Icon size={17}/></button>)}</AnimatedSelection>;
}

export function CollectionSheet({open,onOpenChange,title='Filters and sorting',children,onReset,testId}) {
  return <Sheet open={open} onOpenChange={onOpenChange}><SheetContent side="bottom" className="collection-sheet" data-testid={testId}><SheetHeader className="collection-sheet-header"><SheetTitle className="font-dot">{title}</SheetTitle><SheetDescription className="sr-only">Choose how this collection is displayed. Changes apply immediately.</SheetDescription></SheetHeader><div className="collection-sheet-body">{children}</div><div className="collection-sheet-footer">{onReset && <button className="n-btn-ghost" onClick={onReset}>Reset filters</button>}<button className="n-btn-primary" onClick={()=>onOpenChange(false)}>Done</button></div></SheetContent></Sheet>;
}

export function CollectionChip({children,onRemove}) {
  return <button className="collection-active-chip" onClick={onRemove}>{children}<X size={13} aria-hidden="true"/><span className="sr-only">Remove filter</span></button>;
}
