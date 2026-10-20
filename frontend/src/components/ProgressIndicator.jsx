import { forwardRef, memo } from 'react';
import {clampProgress} from '../lib/progress';
export {clampProgress as progressValue} from '../lib/progress';
const ProgressIndicator=memo(forwardRef(({value,label='Progress',variant='segments',tone='accent',className='',style,...props},ref)=>{
  const unknown=value==null,percent=clampProgress(value);
  return <div ref={ref} role="progressbar" aria-label={label} aria-valuemin={0} aria-valuemax={100} aria-valuenow={unknown?undefined:percent} aria-valuetext={unknown?'Waiting for reported progress':undefined} data-indeterminate={unknown || undefined} data-tone={tone} className={`stak-progress stak-progress--${variant} ${className}`} style={style} {...props}><span className="stak-progress-fill" aria-hidden="true" style={unknown?undefined:{transform:`scaleX(${percent/100})`}}/></div>;
}));
ProgressIndicator.displayName='ProgressIndicator';
export default ProgressIndicator;
