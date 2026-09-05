import{g as n,$ as d,j as l,l as f,d as y,h as i,N as A,b9 as N,ad as x,q as C,ba as W,b as U,n as R,e as V,f as p,bb as D,i as q,s as m,bc as H,bd as X,t as P,v as K,be as G,w as J,x as Q,E as Z}from"./index-Bswk0w89.js";import{C as ee,u as oe}from"./index-BFtk4-Fj.js";const te=n("layout-sider",`
 flex-shrink: 0;
 box-sizing: border-box;
 position: relative;
 z-index: 1;
 color: var(--n-text-color);
 transition:
 color .3s var(--n-bezier),
 border-color .3s var(--n-bezier),
 min-width .3s var(--n-bezier),
 max-width .3s var(--n-bezier),
 transform .3s var(--n-bezier),
 background-color .3s var(--n-bezier);
 background-color: var(--n-color);
 display: flex;
 justify-content: flex-end;
`,[d("bordered",[l("border",`
 content: "";
 position: absolute;
 top: 0;
 bottom: 0;
 width: 1px;
 background-color: var(--n-border-color);
 transition: background-color .3s var(--n-bezier);
 `)]),l("left-placement",[d("bordered",[l("border",`
 right: 0;
 `)])]),d("right-placement",`
 justify-content: flex-start;
 `,[d("bordered",[l("border",`
 left: 0;
 `)]),d("collapsed",[n("layout-toggle-button",[n("base-icon",`
 transform: rotate(180deg);
 `)]),n("layout-toggle-bar",[f("&:hover",[l("top",{transform:"rotate(-12deg) scale(1.15) translateY(-2px)"}),l("bottom",{transform:"rotate(12deg) scale(1.15) translateY(2px)"})])])]),n("layout-toggle-button",`
 left: 0;
 transform: translateX(-50%) translateY(-50%);
 `,[n("base-icon",`
 transform: rotate(0);
 `)]),n("layout-toggle-bar",`
 left: -28px;
 transform: rotate(180deg);
 `,[f("&:hover",[l("top",{transform:"rotate(12deg) scale(1.15) translateY(-2px)"}),l("bottom",{transform:"rotate(-12deg) scale(1.15) translateY(2px)"})])])]),d("collapsed",[n("layout-toggle-bar",[f("&:hover",[l("top",{transform:"rotate(-12deg) scale(1.15) translateY(-2px)"}),l("bottom",{transform:"rotate(12deg) scale(1.15) translateY(2px)"})])]),n("layout-toggle-button",[n("base-icon",`
 transform: rotate(0);
 `)])]),n("layout-toggle-button",`
 transition:
 color .3s var(--n-bezier),
 right .3s var(--n-bezier),
 left .3s var(--n-bezier),
 border-color .3s var(--n-bezier),
 background-color .3s var(--n-bezier);
 cursor: pointer;
 width: 24px;
 height: 24px;
 position: absolute;
 top: 50%;
 right: 0;
 border-radius: 50%;
 display: flex;
 align-items: center;
 justify-content: center;
 font-size: 18px;
 color: var(--n-toggle-button-icon-color);
 border: var(--n-toggle-button-border);
 background-color: var(--n-toggle-button-color);
 box-shadow: 0 2px 4px 0px rgba(0, 0, 0, .06);
 transform: translateX(50%) translateY(-50%);
 z-index: 1;
 `,[n("base-icon",`
 transition: transform .3s var(--n-bezier);
 transform: rotate(180deg);
 `)]),n("layout-toggle-bar",`
 cursor: pointer;
 height: 72px;
 width: 32px;
 position: absolute;
 top: calc(50% - 36px);
 right: -28px;
 `,[l("top, bottom",`
 position: absolute;
 width: 4px;
 border-radius: 2px;
 height: 38px;
 left: 14px;
 transition: 
 background-color .3s var(--n-bezier),
 transform .3s var(--n-bezier);
 `),l("bottom",`
 position: absolute;
 top: 34px;
 `),f("&:hover",[l("top",{transform:"rotate(12deg) scale(1.15) translateY(-2px)"}),l("bottom",{transform:"rotate(-12deg) scale(1.15) translateY(2px)"})]),l("top, bottom",{backgroundColor:"var(--n-toggle-bar-color)"}),f("&:hover",[l("top, bottom",{backgroundColor:"var(--n-toggle-bar-color-hover)"})])]),l("border",`
 position: absolute;
 top: 0;
 right: 0;
 bottom: 0;
 width: 1px;
 transition: background-color .3s var(--n-bezier);
 `),n("layout-sider-scroll-container",`
 flex-grow: 1;
 flex-shrink: 0;
 box-sizing: border-box;
 height: 100%;
 opacity: 0;
 transition: opacity .3s var(--n-bezier);
 max-width: 100%;
 `),d("show-content",[n("layout-sider-scroll-container",{opacity:1})]),d("absolute-positioned",`
 position: absolute;
 left: 0;
 top: 0;
 bottom: 0;
 `)]),re=y({props:{clsPrefix:{type:String,required:!0},onClick:Function},render(){const{clsPrefix:e}=this;return i("div",{onClick:this.onClick,class:`${e}-layout-toggle-bar`},i("div",{class:`${e}-layout-toggle-bar__top`}),i("div",{class:`${e}-layout-toggle-bar__bottom`}))}}),le=y({name:"LayoutToggleButton",props:{clsPrefix:{type:String,required:!0},onClick:Function},render(){const{clsPrefix:e}=this;return i("div",{class:`${e}-layout-toggle-button`,onClick:this.onClick},i(A,{clsPrefix:e},{default:()=>i(ee,null)}))}}),ne={position:D,bordered:Boolean,collapsedWidth:{type:Number,default:48},width:{type:[Number,String],default:272},contentClass:String,contentStyle:{type:[String,Object],default:""},collapseMode:{type:String,default:"transform"},collapsed:{type:Boolean,default:void 0},defaultCollapsed:Boolean,showCollapsedContent:{type:Boolean,default:!0},showTrigger:{type:[Boolean,String],default:!1},nativeScrollbar:{type:Boolean,default:!0},inverted:Boolean,scrollbarProps:Object,triggerClass:String,triggerStyle:[String,Object],collapsedTriggerClass:String,collapsedTriggerStyle:[String,Object],"onUpdate:collapsed":[Function,Array],onUpdateCollapsed:[Function,Array],onAfterEnter:Function,onAfterLeave:Function,onExpand:[Function,Array],onCollapse:[Function,Array],onScroll:Function},ce=y({name:"LayoutSider",props:Object.assign(Object.assign({},R.props),ne),setup(e){const s=q(X),a=C(null),b=C(null),S=C(e.defaultCollapsed),h=oe(P(e,"collapsed"),S),$=p(()=>x(h.value?e.collapsedWidth:e.width)),j=p(()=>e.collapseMode!=="transform"?{}:{minWidth:x(e.width)}),I=p(()=>s?s.siderPlacement:"left");function E(r,o){if(e.nativeScrollbar){const{value:t}=a;t&&(o===void 0?t.scrollTo(r):t.scrollTo(r,o))}else{const{value:t}=b;t&&t.scrollTo(r,o)}}function O(){const{"onUpdate:collapsed":r,onUpdateCollapsed:o,onExpand:t,onCollapse:v}=e,{value:u}=h;o&&m(o,!u),r&&m(r,!u),S.value=!u,u?t&&m(t):v&&m(v)}let T=0,w=0;const M=r=>{var o;const t=r.target;T=t.scrollLeft,w=t.scrollTop,(o=e.onScroll)===null||o===void 0||o.call(e,r)};W(()=>{if(e.nativeScrollbar){const r=a.value;r&&(r.scrollTop=w,r.scrollLeft=T)}}),K(G,{collapsedRef:h,collapseModeRef:P(e,"collapseMode")});const{mergedClsPrefixRef:k,inlineThemeDisabled:B}=U(e),z=R("Layout","-layout-sider",te,H,e,k);function Y(r){var o,t;r.propertyName==="max-width"&&(h.value?(o=e.onAfterLeave)===null||o===void 0||o.call(e):(t=e.onAfterEnter)===null||t===void 0||t.call(e))}const F={scrollTo:E},_=p(()=>{const{common:{cubicBezierEaseInOut:r},self:o}=z.value,{siderToggleButtonColor:t,siderToggleButtonBorder:v,siderToggleBarColor:u,siderToggleBarColorHover:L}=o,c={"--n-bezier":r,"--n-toggle-button-color":t,"--n-toggle-button-border":v,"--n-toggle-bar-color":u,"--n-toggle-bar-color-hover":L};return e.inverted?(c["--n-color"]=o.siderColorInverted,c["--n-text-color"]=o.textColorInverted,c["--n-border-color"]=o.siderBorderColorInverted,c["--n-toggle-button-icon-color"]=o.siderToggleButtonIconColorInverted,c.__invertScrollbar=o.__invertScrollbar):(c["--n-color"]=o.siderColor,c["--n-text-color"]=o.textColor,c["--n-border-color"]=o.siderBorderColor,c["--n-toggle-button-icon-color"]=o.siderToggleButtonIconColor),c}),g=B?V("layout-sider",p(()=>e.inverted?"a":"b"),_,e):void 0;return Object.assign({scrollableElRef:a,scrollbarInstRef:b,mergedClsPrefix:k,mergedTheme:z,styleMaxWidth:$,mergedCollapsed:h,scrollContainerStyle:j,siderPlacement:I,handleNativeElScroll:M,handleTransitionend:Y,handleTriggerClick:O,inlineThemeDisabled:B,cssVars:_,themeClass:g==null?void 0:g.themeClass,onRender:g==null?void 0:g.onRender},F)},render(){var e;const{mergedClsPrefix:s,mergedCollapsed:a,showTrigger:b}=this;return(e=this.onRender)===null||e===void 0||e.call(this),i("aside",{class:[`${s}-layout-sider`,this.themeClass,`${s}-layout-sider--${this.position}-positioned`,`${s}-layout-sider--${this.siderPlacement}-placement`,this.bordered&&`${s}-layout-sider--bordered`,a&&`${s}-layout-sider--collapsed`,(!a||this.showCollapsedContent)&&`${s}-layout-sider--show-content`],onTransitionend:this.handleTransitionend,style:[this.inlineThemeDisabled?void 0:this.cssVars,{maxWidth:this.styleMaxWidth,width:x(this.width)}]},this.nativeScrollbar?i("div",{class:[`${s}-layout-sider-scroll-container`,this.contentClass],onScroll:this.handleNativeElScroll,style:[this.scrollContainerStyle,{overflow:"auto"},this.contentStyle],ref:"scrollableElRef"},this.$slots):i(N,Object.assign({},this.scrollbarProps,{onScroll:this.onScroll,ref:"scrollbarInstRef",style:this.scrollContainerStyle,contentStyle:this.contentStyle,contentClass:this.contentClass,theme:this.mergedTheme.peers.Scrollbar,themeOverrides:this.mergedTheme.peerOverrides.Scrollbar,builtinThemeOverrides:this.inverted&&this.cssVars.__invertScrollbar==="true"?{colorHover:"rgba(255, 255, 255, .4)",color:"rgba(255, 255, 255, .3)"}:void 0}),this.$slots),b?b==="bar"?i(re,{clsPrefix:s,class:a?this.collapsedTriggerClass:this.triggerClass,style:a?this.collapsedTriggerStyle:this.triggerStyle,onClick:this.handleTriggerClick}):i(le,{clsPrefix:s,class:a?this.collapsedTriggerClass:this.triggerClass,style:a?this.collapsedTriggerStyle:this.triggerStyle,onClick:this.handleTriggerClick}):null,this.bordered?i("div",{class:`${s}-layout-sider__border`}):null)}}),se={xmlns:"http://www.w3.org/2000/svg","xmlns:xlink":"http://www.w3.org/1999/xlink",viewBox:"0 0 512 512"},de=y({name:"ChevronDown",render:function(s,a){return J(),Q("svg",se,a[0]||(a[0]=[Z("path",{fill:"none",stroke:"currentColor","stroke-linecap":"round","stroke-linejoin":"round","stroke-width":"48",d:"M112 184l144 144l144-144"},null,-1)]))}});export{de as C,ce as _};
