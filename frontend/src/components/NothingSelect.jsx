import { Children, Fragment, isValidElement, useId } from 'react';
import * as Select from '@radix-ui/react-select';
import { Check, ChevronDown, ChevronUp } from 'lucide-react';

function optionsOf(children) {
  return Children.toArray(children).flatMap(child => !isValidElement(child) ? [] : child.type === Fragment ? optionsOf(child.props.children) : child.type === 'option' ? [child.props] : []);
}
const EMPTY = '__dmm_unselected__';
// Controlled, accessible in-WebView menu. Never launches Android's native select dialog.
export default function NothingSelect({ children, value='', onChange, className='', disabled, name, id, ...props }) {
  const generated = useId();
  const options = optionsOf(children);
  const encode = value => String(value ?? '') || EMPTY;
  const selected = options.find(option => String(option.value ?? '') === String(value ?? ''));
  const label = props['aria-label'] || name || String(props['data-testid'] || 'Choose option').replace(/-/g,' ');
  return <Select.Root value={encode(value)} disabled={disabled} name={name} onValueChange={next => { const chosen=next===EMPTY?'':next; onChange?.({target:{value:chosen},currentTarget:{value:chosen}}); }}>
    <Select.Trigger {...props} id={id || generated} aria-label={label} className={`nothing-select-trigger ${className}`} value={String(value ?? '')}>
      <Select.Value>{selected?.children || 'Choose…'}</Select.Value><Select.Icon><ChevronDown size={14}/></Select.Icon>
    </Select.Trigger>
    <Select.Portal><Select.Content className="nothing-select-menu" data-state="open" position="popper" sideOffset={6} collisionPadding={12} onEscapeKeyDown={event=>event.stopPropagation()}>
      <Select.ScrollUpButton className="nothing-select-scroll"><ChevronUp size={14}/></Select.ScrollUpButton>
      <Select.Viewport className="nothing-select-options">{options.map(option=><Select.Item className="nothing-select-option" key={encode(option.value)} value={encode(option.value)} disabled={option.disabled}><Select.ItemText>{option.children}</Select.ItemText><Select.ItemIndicator><Check size={13}/></Select.ItemIndicator></Select.Item>)}</Select.Viewport>
      <Select.ScrollDownButton className="nothing-select-scroll"><ChevronDown size={14}/></Select.ScrollDownButton>
    </Select.Content></Select.Portal>
  </Select.Root>;
}
