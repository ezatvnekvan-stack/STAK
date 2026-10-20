import { forwardRef } from 'react';
import { Slot } from '@radix-ui/react-slot';
import { cva } from 'class-variance-authority';
import { cn } from '@/lib/utils';
const buttonVariants=cva('stak-button',{
 variants:{variant:{default:'stak-button--primary',primary:'stak-button--primary',secondary:'stak-button--secondary',outline:'stak-button--outlined',outlined:'stak-button--outlined',ghost:'stak-button--subtle',subtle:'stak-button--subtle',destructive:'stak-button--destructive',link:'stak-button--link'},size:{default:'',sm:'stak-button--compact',lg:'stak-button--large',icon:'stak-button--icon'}},defaultVariants:{variant:'default',size:'default'},
});
const Button=forwardRef(({className,variant,size,asChild=false,loading=false,disabled,type,children,...props},ref)=>{
 const Comp=asChild?Slot:'button';
 return <Comp ref={ref} type={asChild?undefined:type || 'button'} disabled={disabled || loading} aria-busy={loading || undefined} className={cn(buttonVariants({variant,size}),className)} {...props}>{!asChild && loading?<><span className="stak-button-loader" aria-hidden="true"/>{children}</>:children}</Comp>;
});
Button.displayName='Button';
export {Button,buttonVariants};
