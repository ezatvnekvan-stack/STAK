import { forwardRef } from 'react';
import { Button } from './ui/button';
import {AnimatedSelection} from './design/MotionSystem';
export { Button };
export function Chip({selected,children,...props}){
  return <Button variant={selected?'primary':'outlined'} size="sm" data-active={selected} aria-pressed={selected} {...props}>{children}</Button>;
}
export const Surface=forwardRef(({as:Tag='section',className='',children,...props},ref)=><Tag ref={ref} className={`stak-card ${className}`} {...props}>{children}</Tag>);
Surface.displayName='Surface';
export function SegmentedControl({label,value,options,onChange}){
  return <AnimatedSelection value={value} className="stak-segments" role="group" aria-label={label}>{options.map(option=><Chip key={option.value} data-value={option.value} selected={value===option.value} onClick={()=>onChange(option.value)} disabled={option.disabled}>{option.label}</Chip>)}</AnimatedSelection>;
}
export const FilterDisclosure=forwardRef(({title='Filters',children,...props},ref)=><details ref={ref} className="stak-disclosure" {...props}><summary className="nothing-disclosure-trigger">{title}</summary><Surface className="mt-3 p-3">{children}</Surface></details>);
FilterDisclosure.displayName='FilterDisclosure';
