import{X as tt,g as f,l as A,j as V,d as L,h as a,b as me,Y as nt,Z as ot,$ as G,a0 as rt,a1 as it,a2 as st,a3 as lt,n as ce,a4 as at,e as we,f as T,a5 as ct,a6 as J,a7 as dt,a8 as ut,N as Oe,a9 as Ae,W as Ee,aa as Fe,ab as Le,ac as pt,ad as ie,ae as mt,c as ft,v as gt,a as Se,r as $e,i as ht,af as vt,w as g,x as R,E as B,z as qe,I,G as n,J as ne,F as k,P as le,R as ue,ag as Ce,ah as We,Q as P,K as O,B as K,ai as Z,M as E,D as C,T as X,U as Q,aj as Ve,ak as Ge,q as F,V as te,_ as pe,S as ae,al as bt,am as Ke,an as ke,ao as yt,H as He,ap as be,A as Ue,C as Xe,aq as wt,ar as kt,as as xt,at as St,O as $t,au as Ct,av as _t}from"./index-Bswk0w89.js";import{b as zt,_ as ee,a as ye,c as Pt,e as Rt,f as It,h as Nt,i as _e,A as Bt}from"./index-BFtk4-Fj.js";import{u as ve}from"./config-BZeWyaXA.js";import{x as Ye,a as Je,b as Tt}from"./xterm-DRSot3bM.js";import{_ as Mt,C as Dt}from"./ChevronDown-DAuz99Li.js";let ze=!1;function jt(){if(tt&&window.CSS&&!ze&&(ze=!0,"registerProperty"in(window==null?void 0:window.CSS)))try{CSS.registerProperty({name:"--n-color-start",syntax:"<color>",inherits:!1,initialValue:"#0000"}),CSS.registerProperty({name:"--n-color-end",syntax:"<color>",inherits:!1,initialValue:"#0000"})}catch{}}function Pe(e,s="default",t=[]){const{children:p}=e;if(p!==null&&typeof p=="object"&&!Array.isArray(p)){const r=p[s];if(typeof r=="function")return r()}return t}const Ot=f("input-group",`
 display: inline-flex;
 width: 100%;
 flex-wrap: nowrap;
 vertical-align: bottom;
`,[A(">",[f("input",[A("&:not(:last-child)",`
 border-top-right-radius: 0!important;
 border-bottom-right-radius: 0!important;
 `),A("&:not(:first-child)",`
 border-top-left-radius: 0!important;
 border-bottom-left-radius: 0!important;
 margin-left: -1px!important;
 `)]),f("button",[A("&:not(:last-child)",`
 border-top-right-radius: 0!important;
 border-bottom-right-radius: 0!important;
 `,[V("state-border, border",`
 border-top-right-radius: 0!important;
 border-bottom-right-radius: 0!important;
 `)]),A("&:not(:first-child)",`
 border-top-left-radius: 0!important;
 border-bottom-left-radius: 0!important;
 `,[V("state-border, border",`
 border-top-left-radius: 0!important;
 border-bottom-left-radius: 0!important;
 `)])]),A("*",[A("&:not(:last-child)",`
 border-top-right-radius: 0!important;
 border-bottom-right-radius: 0!important;
 `,[A(">",[f("input",`
 border-top-right-radius: 0!important;
 border-bottom-right-radius: 0!important;
 `),f("base-selection",[f("base-selection-label",`
 border-top-right-radius: 0!important;
 border-bottom-right-radius: 0!important;
 `),f("base-selection-tags",`
 border-top-right-radius: 0!important;
 border-bottom-right-radius: 0!important;
 `),V("box-shadow, border, state-border",`
 border-top-right-radius: 0!important;
 border-bottom-right-radius: 0!important;
 `)])])]),A("&:not(:first-child)",`
 margin-left: -1px!important;
 border-top-left-radius: 0!important;
 border-bottom-left-radius: 0!important;
 `,[A(">",[f("input",`
 border-top-left-radius: 0!important;
 border-bottom-left-radius: 0!important;
 `),f("base-selection",[f("base-selection-label",`
 border-top-left-radius: 0!important;
 border-bottom-left-radius: 0!important;
 `),f("base-selection-tags",`
 border-top-left-radius: 0!important;
 border-bottom-left-radius: 0!important;
 `),V("box-shadow, border, state-border",`
 border-top-left-radius: 0!important;
 border-bottom-left-radius: 0!important;
 `)])])])])])]),At={},Et=L({name:"InputGroup",props:At,setup(e){const{mergedClsPrefixRef:s}=me(e);return nt("-input-group",Ot,s),{mergedClsPrefix:s}},render(){const{mergedClsPrefix:e}=this;return a("div",{class:`${e}-input-group`},this.$slots)}}),Ft=A([f("descriptions",{fontSize:"var(--n-font-size)"},[f("descriptions-separator",`
 display: inline-block;
 margin: 0 8px 0 2px;
 `),f("descriptions-table-wrapper",[f("descriptions-table",[f("descriptions-table-row",[f("descriptions-table-header",{padding:"var(--n-th-padding)"}),f("descriptions-table-content",{padding:"var(--n-td-padding)"})])])]),ot("bordered",[f("descriptions-table-wrapper",[f("descriptions-table",[f("descriptions-table-row",[A("&:last-child",[f("descriptions-table-content",{paddingBottom:0})])])])])]),G("left-label-placement",[f("descriptions-table-content",[A("> *",{verticalAlign:"top"})])]),G("left-label-align",[A("th",{textAlign:"left"})]),G("center-label-align",[A("th",{textAlign:"center"})]),G("right-label-align",[A("th",{textAlign:"right"})]),G("bordered",[f("descriptions-table-wrapper",`
 border-radius: var(--n-border-radius);
 overflow: hidden;
 background: var(--n-merged-td-color);
 border: 1px solid var(--n-merged-border-color);
 `,[f("descriptions-table",[f("descriptions-table-row",[A("&:not(:last-child)",[f("descriptions-table-content",{borderBottom:"1px solid var(--n-merged-border-color)"}),f("descriptions-table-header",{borderBottom:"1px solid var(--n-merged-border-color)"})]),f("descriptions-table-header",`
 font-weight: 400;
 background-clip: padding-box;
 background-color: var(--n-merged-th-color);
 `,[A("&:not(:last-child)",{borderRight:"1px solid var(--n-merged-border-color)"})]),f("descriptions-table-content",[A("&:not(:last-child)",{borderRight:"1px solid var(--n-merged-border-color)"})])])])])]),f("descriptions-header",`
 font-weight: var(--n-th-font-weight);
 font-size: 18px;
 transition: color .3s var(--n-bezier);
 line-height: var(--n-line-height);
 margin-bottom: 16px;
 color: var(--n-title-text-color);
 `),f("descriptions-table-wrapper",`
 transition:
 background-color .3s var(--n-bezier),
 border-color .3s var(--n-bezier);
 `,[f("descriptions-table",`
 width: 100%;
 border-collapse: separate;
 border-spacing: 0;
 box-sizing: border-box;
 `,[f("descriptions-table-row",`
 box-sizing: border-box;
 transition: border-color .3s var(--n-bezier);
 `,[f("descriptions-table-header",`
 font-weight: var(--n-th-font-weight);
 line-height: var(--n-line-height);
 display: table-cell;
 box-sizing: border-box;
 color: var(--n-th-text-color);
 transition:
 color .3s var(--n-bezier),
 background-color .3s var(--n-bezier),
 border-color .3s var(--n-bezier);
 `),f("descriptions-table-content",`
 vertical-align: top;
 line-height: var(--n-line-height);
 display: table-cell;
 box-sizing: border-box;
 color: var(--n-td-text-color);
 transition:
 color .3s var(--n-bezier),
 background-color .3s var(--n-bezier),
 border-color .3s var(--n-bezier);
 `,[V("content",`
 transition: color .3s var(--n-bezier);
 display: inline-block;
 color: var(--n-td-text-color);
 `)]),V("label",`
 font-weight: var(--n-th-font-weight);
 transition: color .3s var(--n-bezier);
 display: inline-block;
 margin-right: 14px;
 color: var(--n-th-text-color);
 `)])])])]),f("descriptions-table-wrapper",`
 --n-merged-th-color: var(--n-th-color);
 --n-merged-td-color: var(--n-td-color);
 --n-merged-border-color: var(--n-border-color);
 `),rt(f("descriptions-table-wrapper",`
 --n-merged-th-color: var(--n-th-color-modal);
 --n-merged-td-color: var(--n-td-color-modal);
 --n-merged-border-color: var(--n-border-color-modal);
 `)),it(f("descriptions-table-wrapper",`
 --n-merged-th-color: var(--n-th-color-popover);
 --n-merged-td-color: var(--n-td-color-popover);
 --n-merged-border-color: var(--n-border-color-popover);
 `))]),Ze="DESCRIPTION_ITEM_FLAG";function Lt(e){return typeof e=="object"&&e&&!Array.isArray(e)?e.type&&e.type[Ze]:!1}const qt=Object.assign(Object.assign({},ce.props),{title:String,column:{type:Number,default:3},columns:Number,labelPlacement:{type:String,default:"top"},labelAlign:{type:String,default:"left"},separator:{type:String,default:":"},size:String,bordered:Boolean,labelClass:String,labelStyle:[Object,String],contentClass:String,contentStyle:[Object,String]}),Wt=L({name:"Descriptions",props:qt,slots:Object,setup(e){const{mergedClsPrefixRef:s,inlineThemeDisabled:t,mergedComponentPropsRef:p}=me(e),r=T(()=>{var i,u;return e.size||((u=(i=p==null?void 0:p.value)===null||i===void 0?void 0:i.Descriptions)===null||u===void 0?void 0:u.size)||"medium"}),l=ce("Descriptions","-descriptions",Ft,at,e,s),d=T(()=>{const{bordered:i}=e,u=r.value,{common:{cubicBezierEaseInOut:S},self:{titleTextColor:o,thColor:v,thColorModal:w,thColorPopover:z,thTextColor:_,thFontWeight:x,tdTextColor:$,tdColor:b,tdColorModal:c,tdColorPopover:y,borderColor:h,borderColorModal:j,borderColorPopover:H,borderRadius:W,lineHeight:U,[J("fontSize",u)]:M,[J(i?"thPaddingBordered":"thPadding",u)]:D,[J(i?"tdPaddingBordered":"tdPadding",u)]:N}}=l.value;return{"--n-title-text-color":o,"--n-th-padding":D,"--n-td-padding":N,"--n-font-size":M,"--n-bezier":S,"--n-th-font-weight":x,"--n-line-height":U,"--n-th-text-color":_,"--n-td-text-color":$,"--n-th-color":v,"--n-th-color-modal":w,"--n-th-color-popover":z,"--n-td-color":b,"--n-td-color-modal":c,"--n-td-color-popover":y,"--n-border-radius":W,"--n-border-color":h,"--n-border-color-modal":j,"--n-border-color-popover":H}}),m=t?we("descriptions",T(()=>{let i="";const{bordered:u}=e;return u&&(i+="a"),i+=r.value[0],i}),d,e):void 0;return{mergedClsPrefix:s,cssVars:t?void 0:d,themeClass:m==null?void 0:m.themeClass,onRender:m==null?void 0:m.onRender,compitableColumn:ct(e,["columns","column"]),inlineThemeDisabled:t,mergedSize:r}},render(){const e=this.$slots.default,s=e?st(e()):[];s.length;const{contentClass:t,labelClass:p,compitableColumn:r,labelPlacement:l,labelAlign:d,mergedSize:m,bordered:i,title:u,cssVars:S,mergedClsPrefix:o,separator:v,onRender:w}=this;w==null||w();const z=s.filter(b=>Lt(b)),_={span:0,row:[],secondRow:[],rows:[]},$=z.reduce((b,c,y)=>{const h=c.props||{},j=z.length-1===y,H=["label"in h?h.label:Pe(c,"label")],W=[Pe(c)],U=h.span||1,M=b.span;b.span+=U;const D=h.labelStyle||h["label-style"]||this.labelStyle,N=h.contentStyle||h["content-style"]||this.contentStyle;if(l==="left")i?b.row.push(a("th",{class:[`${o}-descriptions-table-header`,p],colspan:1,style:D},H),a("td",{class:[`${o}-descriptions-table-content`,t],colspan:j?(r-M)*2+1:U*2-1,style:N},W)):b.row.push(a("td",{class:`${o}-descriptions-table-content`,colspan:j?(r-M)*2:U*2},a("span",{class:[`${o}-descriptions-table-content__label`,p],style:D},[...H,v&&a("span",{class:`${o}-descriptions-separator`},v)]),a("span",{class:[`${o}-descriptions-table-content__content`,t],style:N},W)));else{const q=j?(r-M)*2:U*2;b.row.push(a("th",{class:[`${o}-descriptions-table-header`,p],colspan:q,style:D},H)),b.secondRow.push(a("td",{class:[`${o}-descriptions-table-content`,t],colspan:q,style:N},W))}return(b.span>=r||j)&&(b.span=0,b.row.length&&(b.rows.push(b.row),b.row=[]),l!=="left"&&b.secondRow.length&&(b.rows.push(b.secondRow),b.secondRow=[])),b},_).rows.map(b=>a("tr",{class:`${o}-descriptions-table-row`},b));return a("div",{style:S,class:[`${o}-descriptions`,this.themeClass,`${o}-descriptions--${l}-label-placement`,`${o}-descriptions--${d}-label-align`,`${o}-descriptions--${m}-size`,i&&`${o}-descriptions--bordered`]},u||this.$slots.header?a("div",{class:`${o}-descriptions-header`},u||zt(this,"header")):null,a("div",{class:`${o}-descriptions-table-wrapper`},a("table",{class:`${o}-descriptions-table`},a("tbody",null,l==="top"&&a("tr",{class:`${o}-descriptions-table-row`,style:{visibility:"collapse"}},lt(r*2,a("td",null))),$))))}}),Vt={label:String,span:{type:Number,default:1},labelClass:String,labelStyle:[Object,String],contentClass:String,contentStyle:[Object,String]},re=L({name:"DescriptionsItem",[Ze]:!0,props:Vt,slots:Object,render(){return null}});function Gt(e){const{textColor3:s,infoColor:t,errorColor:p,successColor:r,warningColor:l,textColor1:d,textColor2:m,railColor:i,fontWeightStrong:u,fontSize:S}=e;return Object.assign(Object.assign({},ut),{contentFontSize:S,titleFontWeight:u,circleBorder:`2px solid ${s}`,circleBorderInfo:`2px solid ${t}`,circleBorderError:`2px solid ${p}`,circleBorderSuccess:`2px solid ${r}`,circleBorderWarning:`2px solid ${l}`,iconColor:s,iconColorInfo:t,iconColorError:p,iconColorSuccess:r,iconColorWarning:l,titleTextColor:d,contentTextColor:m,metaTextColor:s,lineColor:i})}const Kt={common:dt,self:Gt},Ht={success:a(Le,null),error:a(Fe,null),warning:a(Ee,null),info:a(Ae,null)},Ut=L({name:"ProgressCircle",props:{clsPrefix:{type:String,required:!0},status:{type:String,required:!0},strokeWidth:{type:Number,required:!0},fillColor:[String,Object],railColor:String,railStyle:[String,Object],percentage:{type:Number,default:0},offsetDegree:{type:Number,default:0},showIndicator:{type:Boolean,required:!0},indicatorTextColor:String,unit:String,viewBoxWidth:{type:Number,required:!0},gapDegree:{type:Number,required:!0},gapOffsetDegree:{type:Number,default:0}},setup(e,{slots:s}){const t=T(()=>{const l="gradient",{fillColor:d}=e;return typeof d=="object"?`${l}-${pt(JSON.stringify(d))}`:l});function p(l,d,m,i){const{gapDegree:u,viewBoxWidth:S,strokeWidth:o}=e,v=50,w=0,z=v,_=0,x=2*v,$=50+o/2,b=`M ${$},${$} m ${w},${z}
      a ${v},${v} 0 1 1 ${_},${-x}
      a ${v},${v} 0 1 1 ${-_},${x}`,c=Math.PI*2*v,y={stroke:i==="rail"?m:typeof e.fillColor=="object"?`url(#${t.value})`:m,strokeDasharray:`${Math.min(l,100)/100*(c-u)}px ${S*8}px`,strokeDashoffset:`-${u/2}px`,transformOrigin:d?"center":void 0,transform:d?`rotate(${d}deg)`:void 0};return{pathString:b,pathStyle:y}}const r=()=>{const l=typeof e.fillColor=="object",d=l?e.fillColor.stops[0]:"",m=l?e.fillColor.stops[1]:"";return l&&a("defs",null,a("linearGradient",{id:t.value,x1:"0%",y1:"100%",x2:"100%",y2:"0%"},a("stop",{offset:"0%","stop-color":d}),a("stop",{offset:"100%","stop-color":m})))};return()=>{const{fillColor:l,railColor:d,strokeWidth:m,offsetDegree:i,status:u,percentage:S,showIndicator:o,indicatorTextColor:v,unit:w,gapOffsetDegree:z,clsPrefix:_}=e,{pathString:x,pathStyle:$}=p(100,0,d,"rail"),{pathString:b,pathStyle:c}=p(S,i,l,"fill"),y=100+m;return a("div",{class:`${_}-progress-content`,role:"none"},a("div",{class:`${_}-progress-graph`,"aria-hidden":!0},a("div",{class:`${_}-progress-graph-circle`,style:{transform:z?`rotate(${z}deg)`:void 0}},a("svg",{viewBox:`0 0 ${y} ${y}`},r(),a("g",null,a("path",{class:`${_}-progress-graph-circle-rail`,d:x,"stroke-width":m,"stroke-linecap":"round",fill:"none",style:$})),a("g",null,a("path",{class:[`${_}-progress-graph-circle-fill`,S===0&&`${_}-progress-graph-circle-fill--empty`],d:b,"stroke-width":m,"stroke-linecap":"round",fill:"none",style:c}))))),o?a("div",null,s.default?a("div",{class:`${_}-progress-custom-content`,role:"none"},s.default()):u!=="default"?a("div",{class:`${_}-progress-icon`,"aria-hidden":!0},a(Oe,{clsPrefix:_},{default:()=>Ht[u]})):a("div",{class:`${_}-progress-text`,style:{color:v},role:"none"},a("span",{class:`${_}-progress-text__percentage`},S),a("span",{class:`${_}-progress-text__unit`},w))):null)}}}),Xt={success:a(Le,null),error:a(Fe,null),warning:a(Ee,null),info:a(Ae,null)},Yt=L({name:"ProgressLine",props:{clsPrefix:{type:String,required:!0},percentage:{type:Number,default:0},railColor:String,railStyle:[String,Object],fillColor:[String,Object],status:{type:String,required:!0},indicatorPlacement:{type:String,required:!0},indicatorTextColor:String,unit:{type:String,default:"%"},processing:{type:Boolean,required:!0},showIndicator:{type:Boolean,required:!0},height:[String,Number],railBorderRadius:[String,Number],fillBorderRadius:[String,Number]},setup(e,{slots:s}){const t=T(()=>ie(e.height)),p=T(()=>{var d,m;return typeof e.fillColor=="object"?`linear-gradient(to right, ${(d=e.fillColor)===null||d===void 0?void 0:d.stops[0]} , ${(m=e.fillColor)===null||m===void 0?void 0:m.stops[1]})`:e.fillColor}),r=T(()=>e.railBorderRadius!==void 0?ie(e.railBorderRadius):e.height!==void 0?ie(e.height,{c:.5}):""),l=T(()=>e.fillBorderRadius!==void 0?ie(e.fillBorderRadius):e.railBorderRadius!==void 0?ie(e.railBorderRadius):e.height!==void 0?ie(e.height,{c:.5}):"");return()=>{const{indicatorPlacement:d,railColor:m,railStyle:i,percentage:u,unit:S,indicatorTextColor:o,status:v,showIndicator:w,processing:z,clsPrefix:_}=e;return a("div",{class:`${_}-progress-content`,role:"none"},a("div",{class:`${_}-progress-graph`,"aria-hidden":!0},a("div",{class:[`${_}-progress-graph-line`,{[`${_}-progress-graph-line--indicator-${d}`]:!0}]},a("div",{class:`${_}-progress-graph-line-rail`,style:[{backgroundColor:m,height:t.value,borderRadius:r.value},i]},a("div",{class:[`${_}-progress-graph-line-fill`,z&&`${_}-progress-graph-line-fill--processing`],style:{maxWidth:`${e.percentage}%`,background:p.value,height:t.value,lineHeight:t.value,borderRadius:l.value}},d==="inside"?a("div",{class:`${_}-progress-graph-line-indicator`,style:{color:o}},s.default?s.default():`${u}${S}`):null)))),w&&d==="outside"?a("div",null,s.default?a("div",{class:`${_}-progress-custom-content`,style:{color:o},role:"none"},s.default()):v==="default"?a("div",{role:"none",class:`${_}-progress-icon ${_}-progress-icon--as-text`,style:{color:o}},u,S):a("div",{class:`${_}-progress-icon`,"aria-hidden":!0},a(Oe,{clsPrefix:_},{default:()=>Xt[v]}))):null)}}});function Re(e,s,t=100){return`m ${t/2} ${t/2-e} a ${e} ${e} 0 1 1 0 ${2*e} a ${e} ${e} 0 1 1 0 -${2*e}`}const Jt=L({name:"ProgressMultipleCircle",props:{clsPrefix:{type:String,required:!0},viewBoxWidth:{type:Number,required:!0},percentage:{type:Array,default:[0]},strokeWidth:{type:Number,required:!0},circleGap:{type:Number,required:!0},showIndicator:{type:Boolean,required:!0},fillColor:{type:Array,default:()=>[]},railColor:{type:Array,default:()=>[]},railStyle:{type:Array,default:()=>[]}},setup(e,{slots:s}){const t=T(()=>e.percentage.map((l,d)=>`${Math.PI*l/100*(e.viewBoxWidth/2-e.strokeWidth/2*(1+2*d)-e.circleGap*d)*2}, ${e.viewBoxWidth*8}`)),p=(r,l)=>{const d=e.fillColor[l],m=typeof d=="object"?d.stops[0]:"",i=typeof d=="object"?d.stops[1]:"";return typeof e.fillColor[l]=="object"&&a("linearGradient",{id:`gradient-${l}`,x1:"100%",y1:"0%",x2:"0%",y2:"100%"},a("stop",{offset:"0%","stop-color":m}),a("stop",{offset:"100%","stop-color":i}))};return()=>{const{viewBoxWidth:r,strokeWidth:l,circleGap:d,showIndicator:m,fillColor:i,railColor:u,railStyle:S,percentage:o,clsPrefix:v}=e;return a("div",{class:`${v}-progress-content`,role:"none"},a("div",{class:`${v}-progress-graph`,"aria-hidden":!0},a("div",{class:`${v}-progress-graph-circle`},a("svg",{viewBox:`0 0 ${r} ${r}`},a("defs",null,o.map((w,z)=>p(w,z))),o.map((w,z)=>a("g",{key:z},a("path",{class:`${v}-progress-graph-circle-rail`,d:Re(r/2-l/2*(1+2*z)-d*z,l,r),"stroke-width":l,"stroke-linecap":"round",fill:"none",style:[{strokeDashoffset:0,stroke:u[z]},S[z]]}),a("path",{class:[`${v}-progress-graph-circle-fill`,w===0&&`${v}-progress-graph-circle-fill--empty`],d:Re(r/2-l/2*(1+2*z)-d*z,l,r),"stroke-width":l,"stroke-linecap":"round",fill:"none",style:{strokeDasharray:t.value[z],strokeDashoffset:0,stroke:typeof i[z]=="object"?`url(#gradient-${z})`:i[z]}})))))),m&&s.default?a("div",null,a("div",{class:`${v}-progress-text`},s.default())):null)}}}),Zt=A([f("progress",{display:"inline-block"},[f("progress-icon",`
 color: var(--n-icon-color);
 transition: color .3s var(--n-bezier);
 `),G("line",`
 width: 100%;
 display: block;
 `,[f("progress-content",`
 display: flex;
 align-items: center;
 `,[f("progress-graph",{flex:1})]),f("progress-custom-content",{marginLeft:"14px"}),f("progress-icon",`
 width: 30px;
 padding-left: 14px;
 height: var(--n-icon-size-line);
 line-height: var(--n-icon-size-line);
 font-size: var(--n-icon-size-line);
 `,[G("as-text",`
 color: var(--n-text-color-line-outer);
 text-align: center;
 width: 40px;
 font-size: var(--n-font-size);
 padding-left: 4px;
 transition: color .3s var(--n-bezier);
 `)])]),G("circle, dashboard",{width:"120px"},[f("progress-custom-content",`
 position: absolute;
 left: 50%;
 top: 50%;
 transform: translateX(-50%) translateY(-50%);
 display: flex;
 align-items: center;
 justify-content: center;
 `),f("progress-text",`
 position: absolute;
 left: 50%;
 top: 50%;
 transform: translateX(-50%) translateY(-50%);
 display: flex;
 align-items: center;
 color: inherit;
 font-size: var(--n-font-size-circle);
 color: var(--n-text-color-circle);
 font-weight: var(--n-font-weight-circle);
 transition: color .3s var(--n-bezier);
 white-space: nowrap;
 `),f("progress-icon",`
 position: absolute;
 left: 50%;
 top: 50%;
 transform: translateX(-50%) translateY(-50%);
 display: flex;
 align-items: center;
 color: var(--n-icon-color);
 font-size: var(--n-icon-size-circle);
 `)]),G("multiple-circle",`
 width: 200px;
 color: inherit;
 `,[f("progress-text",`
 font-weight: var(--n-font-weight-circle);
 color: var(--n-text-color-circle);
 position: absolute;
 left: 50%;
 top: 50%;
 transform: translateX(-50%) translateY(-50%);
 display: flex;
 align-items: center;
 justify-content: center;
 transition: color .3s var(--n-bezier);
 `)]),f("progress-content",{position:"relative"}),f("progress-graph",{position:"relative"},[f("progress-graph-circle",[A("svg",{verticalAlign:"bottom"}),f("progress-graph-circle-fill",`
 stroke: var(--n-fill-color);
 transition:
 opacity .3s var(--n-bezier),
 stroke .3s var(--n-bezier),
 stroke-dasharray .3s var(--n-bezier);
 `,[G("empty",{opacity:0})]),f("progress-graph-circle-rail",`
 transition: stroke .3s var(--n-bezier);
 overflow: hidden;
 stroke: var(--n-rail-color);
 `)]),f("progress-graph-line",[G("indicator-inside",[f("progress-graph-line-rail",`
 height: 16px;
 line-height: 16px;
 border-radius: 10px;
 `,[f("progress-graph-line-fill",`
 height: inherit;
 border-radius: 10px;
 `),f("progress-graph-line-indicator",`
 background: #0000;
 white-space: nowrap;
 text-align: right;
 margin-left: 14px;
 margin-right: 14px;
 height: inherit;
 font-size: 12px;
 color: var(--n-text-color-line-inner);
 transition: color .3s var(--n-bezier);
 `)])]),G("indicator-inside-label",`
 height: 16px;
 display: flex;
 align-items: center;
 `,[f("progress-graph-line-rail",`
 flex: 1;
 transition: background-color .3s var(--n-bezier);
 `),f("progress-graph-line-indicator",`
 background: var(--n-fill-color);
 font-size: 12px;
 transform: translateZ(0);
 display: flex;
 vertical-align: middle;
 height: 16px;
 line-height: 16px;
 padding: 0 10px;
 border-radius: 10px;
 position: absolute;
 white-space: nowrap;
 color: var(--n-text-color-line-inner);
 transition:
 right .2s var(--n-bezier),
 color .3s var(--n-bezier),
 background-color .3s var(--n-bezier);
 `)]),f("progress-graph-line-rail",`
 position: relative;
 overflow: hidden;
 height: var(--n-rail-height);
 border-radius: 5px;
 background-color: var(--n-rail-color);
 transition: background-color .3s var(--n-bezier);
 `,[f("progress-graph-line-fill",`
 background: var(--n-fill-color);
 position: relative;
 border-radius: 5px;
 height: inherit;
 width: 100%;
 max-width: 0%;
 transition:
 background-color .3s var(--n-bezier),
 max-width .2s var(--n-bezier);
 `,[G("processing",[A("&::after",`
 content: "";
 background-image: var(--n-line-bg-processing);
 animation: progress-processing-animation 2s var(--n-bezier) infinite;
 `)])])])])])]),A("@keyframes progress-processing-animation",`
 0% {
 position: absolute;
 left: 0;
 top: 0;
 bottom: 0;
 right: 100%;
 opacity: 1;
 }
 66% {
 position: absolute;
 left: 0;
 top: 0;
 bottom: 0;
 right: 0;
 opacity: 0;
 }
 100% {
 position: absolute;
 left: 0;
 top: 0;
 bottom: 0;
 right: 0;
 opacity: 0;
 }
 `)]),Qt=Object.assign(Object.assign({},ce.props),{processing:Boolean,type:{type:String,default:"line"},gapDegree:Number,gapOffsetDegree:Number,status:{type:String,default:"default"},railColor:[String,Array],railStyle:[String,Array],color:[String,Array,Object],viewBoxWidth:{type:Number,default:100},strokeWidth:{type:Number,default:7},percentage:[Number,Array],unit:{type:String,default:"%"},showIndicator:{type:Boolean,default:!0},indicatorPosition:{type:String,default:"outside"},indicatorPlacement:{type:String,default:"outside"},indicatorTextColor:String,circleGap:{type:Number,default:1},height:Number,borderRadius:[String,Number],fillBorderRadius:[String,Number],offsetDegree:Number}),en=L({name:"Progress",props:Qt,setup(e){const s=T(()=>e.indicatorPlacement||e.indicatorPosition),t=T(()=>{if(e.gapDegree||e.gapDegree===0)return e.gapDegree;if(e.type==="dashboard")return 75}),{mergedClsPrefixRef:p,inlineThemeDisabled:r}=me(e),l=ce("Progress","-progress",Zt,mt,e,p),d=T(()=>{const{status:i}=e,{common:{cubicBezierEaseInOut:u},self:{fontSize:S,fontSizeCircle:o,railColor:v,railHeight:w,iconSizeCircle:z,iconSizeLine:_,textColorCircle:x,textColorLineInner:$,textColorLineOuter:b,lineBgProcessing:c,fontWeightCircle:y,[J("iconColor",i)]:h,[J("fillColor",i)]:j}}=l.value;return{"--n-bezier":u,"--n-fill-color":j,"--n-font-size":S,"--n-font-size-circle":o,"--n-font-weight-circle":y,"--n-icon-color":h,"--n-icon-size-circle":z,"--n-icon-size-line":_,"--n-line-bg-processing":c,"--n-rail-color":v,"--n-rail-height":w,"--n-text-color-circle":x,"--n-text-color-line-inner":$,"--n-text-color-line-outer":b}}),m=r?we("progress",T(()=>e.status[0]),d,e):void 0;return{mergedClsPrefix:p,mergedIndicatorPlacement:s,gapDeg:t,cssVars:r?void 0:d,themeClass:m==null?void 0:m.themeClass,onRender:m==null?void 0:m.onRender}},render(){const{type:e,cssVars:s,indicatorTextColor:t,showIndicator:p,status:r,railColor:l,railStyle:d,color:m,percentage:i,viewBoxWidth:u,strokeWidth:S,mergedIndicatorPlacement:o,unit:v,borderRadius:w,fillBorderRadius:z,height:_,processing:x,circleGap:$,mergedClsPrefix:b,gapDeg:c,gapOffsetDegree:y,themeClass:h,$slots:j,onRender:H}=this;return H==null||H(),a("div",{class:[h,`${b}-progress`,`${b}-progress--${e}`,`${b}-progress--${r}`],style:s,"aria-valuemax":100,"aria-valuemin":0,"aria-valuenow":i,role:e==="circle"||e==="line"||e==="dashboard"?"progressbar":"none"},e==="circle"||e==="dashboard"?a(Ut,{clsPrefix:b,status:r,showIndicator:p,indicatorTextColor:t,railColor:l,fillColor:m,railStyle:d,offsetDegree:this.offsetDegree,percentage:i,viewBoxWidth:u,strokeWidth:S,gapDegree:c===void 0?e==="dashboard"?75:0:c,gapOffsetDegree:y,unit:v},j):e==="line"?a(Yt,{clsPrefix:b,status:r,showIndicator:p,indicatorTextColor:t,railColor:l,fillColor:m,railStyle:d,percentage:i,processing:x,indicatorPlacement:o,unit:v,fillBorderRadius:z,railBorderRadius:w,height:_},j):e==="multiple-circle"?a(Jt,{clsPrefix:b,strokeWidth:S,railColor:l,fillColor:m,railStyle:d,viewBoxWidth:u,percentage:i,showIndicator:p,circleGap:$},j):null)}}),Ie=1.25,tn=f("timeline",`
 position: relative;
 width: 100%;
 display: flex;
 flex-direction: column;
 line-height: ${Ie};
`,[G("horizontal",`
 flex-direction: row;
 `,[A(">",[f("timeline-item",`
 flex-shrink: 0;
 padding-right: 40px;
 `,[G("dashed-line-type",[A(">",[f("timeline-item-timeline",[V("line",`
 background-image: linear-gradient(90deg, var(--n-color-start), var(--n-color-start) 50%, transparent 50%, transparent 100%);
 background-size: 10px 1px;
 `)])])]),A(">",[f("timeline-item-content",`
 margin-top: calc(var(--n-icon-size) + 12px);
 `,[A(">",[V("meta",`
 margin-top: 6px;
 margin-bottom: unset;
 `)])]),f("timeline-item-timeline",`
 width: 100%;
 height: calc(var(--n-icon-size) + 12px);
 `,[V("line",`
 left: var(--n-icon-size);
 top: calc(var(--n-icon-size) / 2 - 1px);
 right: 0px;
 width: unset;
 height: 2px;
 `)])])])])]),G("right-placement",[f("timeline-item",[f("timeline-item-content",`
 text-align: right;
 margin-right: calc(var(--n-icon-size) + 12px);
 `),f("timeline-item-timeline",`
 width: var(--n-icon-size);
 right: 0;
 `)])]),G("left-placement",[f("timeline-item",[f("timeline-item-content",`
 margin-left: calc(var(--n-icon-size) + 12px);
 `),f("timeline-item-timeline",`
 left: 0;
 `)])]),f("timeline-item",`
 position: relative;
 `,[A("&:last-child",[f("timeline-item-timeline",[V("line",`
 display: none;
 `)]),f("timeline-item-content",[V("meta",`
 margin-bottom: 0;
 `)])]),f("timeline-item-content",[V("title",`
 margin: var(--n-title-margin);
 font-size: var(--n-title-font-size);
 transition: color .3s var(--n-bezier);
 font-weight: var(--n-title-font-weight);
 color: var(--n-title-text-color);
 `),V("content",`
 transition: color .3s var(--n-bezier);
 font-size: var(--n-content-font-size);
 color: var(--n-content-text-color);
 `),V("meta",`
 transition: color .3s var(--n-bezier);
 font-size: 12px;
 margin-top: 6px;
 margin-bottom: 20px;
 color: var(--n-meta-text-color);
 `)]),G("dashed-line-type",[f("timeline-item-timeline",[V("line",`
 --n-color-start: var(--n-line-color);
 transition: --n-color-start .3s var(--n-bezier);
 background-color: transparent;
 background-image: linear-gradient(180deg, var(--n-color-start), var(--n-color-start) 50%, transparent 50%, transparent 100%);
 background-size: 1px 10px;
 `)])]),f("timeline-item-timeline",`
 width: calc(var(--n-icon-size) + 12px);
 position: absolute;
 top: calc(var(--n-title-font-size) * ${Ie} / 2 - var(--n-icon-size) / 2);
 height: 100%;
 `,[V("circle",`
 border: var(--n-circle-border);
 transition:
 background-color .3s var(--n-bezier),
 border-color .3s var(--n-bezier);
 width: var(--n-icon-size);
 height: var(--n-icon-size);
 border-radius: var(--n-icon-size);
 box-sizing: border-box;
 `),V("icon",`
 color: var(--n-icon-color);
 font-size: var(--n-icon-size);
 height: var(--n-icon-size);
 width: var(--n-icon-size);
 display: flex;
 align-items: center;
 justify-content: center;
 `),V("line",`
 transition: background-color .3s var(--n-bezier);
 position: absolute;
 top: var(--n-icon-size);
 left: calc(var(--n-icon-size) / 2 - 1px);
 bottom: 0px;
 width: 2px;
 background-color: var(--n-line-color);
 `)])])]),nn=Object.assign(Object.assign({},ce.props),{horizontal:Boolean,itemPlacement:{type:String,default:"left"},size:{type:String,default:"medium"},iconSize:Number}),Qe=ft("n-timeline"),Ne=L({name:"Timeline",props:nn,setup(e,{slots:s}){const{mergedClsPrefixRef:t}=me(e),p=ce("Timeline","-timeline",tn,Kt,e,t);return gt(Qe,{props:e,mergedThemeRef:p,mergedClsPrefixRef:t}),()=>{const{value:r}=t;return a("div",{class:[`${r}-timeline`,e.horizontal&&`${r}-timeline--horizontal`,`${r}-timeline--${e.size}-size`,!e.horizontal&&`${r}-timeline--${e.itemPlacement}-placement`]},s)}}}),on={time:[String,Number],title:String,content:String,color:String,lineType:{type:String,default:"default"},type:{type:String,default:"default"}},Be=L({name:"TimelineItem",props:on,slots:Object,setup(e){const s=ht(Qe);s||vt("timeline-item","`n-timeline-item` must be placed inside `n-timeline`."),jt();const{inlineThemeDisabled:t}=me(),p=T(()=>{const{props:{size:l,iconSize:d},mergedThemeRef:m}=s,{type:i}=e,{self:{titleTextColor:u,contentTextColor:S,metaTextColor:o,lineColor:v,titleFontWeight:w,contentFontSize:z,[J("iconSize",l)]:_,[J("titleMargin",l)]:x,[J("titleFontSize",l)]:$,[J("circleBorder",i)]:b,[J("iconColor",i)]:c},common:{cubicBezierEaseInOut:y}}=m.value;return{"--n-bezier":y,"--n-circle-border":b,"--n-icon-color":c,"--n-content-font-size":z,"--n-content-text-color":S,"--n-line-color":v,"--n-meta-text-color":o,"--n-title-font-size":$,"--n-title-font-weight":w,"--n-title-margin":x,"--n-title-text-color":u,"--n-icon-size":ie(d)||_}}),r=t?we("timeline-item",T(()=>{const{props:{size:l,iconSize:d}}=s,{type:m}=e;return`${l[0]}${d||"a"}${m[0]}`}),p,s.props):void 0;return{mergedClsPrefix:s.mergedClsPrefixRef,cssVars:t?void 0:p,themeClass:r==null?void 0:r.themeClass,onRender:r==null?void 0:r.onRender}},render(){const{mergedClsPrefix:e,color:s,onRender:t,$slots:p}=this;return t==null||t(),a("div",{class:[`${e}-timeline-item`,this.themeClass,`${e}-timeline-item--${this.type}-type`,`${e}-timeline-item--${this.lineType}-line-type`],style:this.cssVars},a("div",{class:`${e}-timeline-item-timeline`},a("div",{class:`${e}-timeline-item-timeline__line`}),Se(p.icon,r=>r?a("div",{class:`${e}-timeline-item-timeline__icon`,style:{color:s}},r):a("div",{class:`${e}-timeline-item-timeline__circle`,style:{borderColor:s}}))),a("div",{class:`${e}-timeline-item-content`},Se(p.header,r=>r||this.title?a("div",{class:`${e}-timeline-item-content__title`},r||this.title):null),a("div",{class:`${e}-timeline-item-content__content`},$e(p.default,()=>[this.content])),a("div",{class:`${e}-timeline-item-content__meta`},$e(p.footer,()=>[this.time]))))}}),rn={xmlns:"http://www.w3.org/2000/svg","xmlns:xlink":"http://www.w3.org/1999/xlink",viewBox:"0 0 512 512"},sn=L({name:"ArrowDownOutline",render:function(s,t){return g(),R("svg",rn,t[0]||(t[0]=[B("path",{fill:"none",stroke:"currentColor","stroke-linecap":"round","stroke-linejoin":"round","stroke-width":"48",d:"M112 268l144 144l144-144"},null,-1),B("path",{fill:"none",stroke:"currentColor","stroke-linecap":"round","stroke-linejoin":"round","stroke-width":"48",d:"M256 392V100"},null,-1)]))}}),ln={xmlns:"http://www.w3.org/2000/svg","xmlns:xlink":"http://www.w3.org/1999/xlink",viewBox:"0 0 512 512"},et=L({name:"ChevronDownOutline",render:function(s,t){return g(),R("svg",ln,t[0]||(t[0]=[B("path",{fill:"none",stroke:"currentColor","stroke-linecap":"round","stroke-linejoin":"round","stroke-width":"48",d:"M112 184l144 144l144-144"},null,-1)]))}}),an={xmlns:"http://www.w3.org/2000/svg","xmlns:xlink":"http://www.w3.org/1999/xlink",viewBox:"0 0 512 512"},cn=L({name:"ChevronForwardOutline",render:function(s,t){return g(),R("svg",an,t[0]||(t[0]=[B("path",{fill:"none",stroke:"currentColor","stroke-linecap":"round","stroke-linejoin":"round","stroke-width":"48",d:"M184 112l144 144l-144 144"},null,-1)]))}}),dn={xmlns:"http://www.w3.org/2000/svg","xmlns:xlink":"http://www.w3.org/1999/xlink",viewBox:"0 0 512 512"},un=L({name:"ChevronUp",render:function(s,t){return g(),R("svg",dn,t[0]||(t[0]=[B("path",{fill:"none",stroke:"currentColor","stroke-linecap":"round","stroke-linejoin":"round","stroke-width":"48",d:"M112 328l144-144l144 144"},null,-1)]))}}),pn={xmlns:"http://www.w3.org/2000/svg","xmlns:xlink":"http://www.w3.org/1999/xlink",viewBox:"0 0 512 512"},mn=L({name:"ChevronUpOutline",render:function(s,t){return g(),R("svg",pn,t[0]||(t[0]=[B("path",{fill:"none",stroke:"currentColor","stroke-linecap":"round","stroke-linejoin":"round","stroke-width":"48",d:"M112 328l144-144l144 144"},null,-1)]))}}),fn={xmlns:"http://www.w3.org/2000/svg","xmlns:xlink":"http://www.w3.org/1999/xlink",viewBox:"0 0 512 512"},Te=L({name:"DocumentTextOutline",render:function(s,t){return g(),R("svg",fn,t[0]||(t[0]=[B("path",{d:"M416 221.25V416a48 48 0 0 1-48 48H144a48 48 0 0 1-48-48V96a48 48 0 0 1 48-48h98.75a32 32 0 0 1 22.62 9.37l141.26 141.26a32 32 0 0 1 9.37 22.62z",fill:"none",stroke:"currentColor","stroke-linejoin":"round","stroke-width":"32"},null,-1),B("path",{d:"M256 56v120a32 32 0 0 0 32 32h120",fill:"none",stroke:"currentColor","stroke-linecap":"round","stroke-linejoin":"round","stroke-width":"32"},null,-1),B("path",{fill:"none",stroke:"currentColor","stroke-linecap":"round","stroke-linejoin":"round","stroke-width":"32",d:"M176 288h160"},null,-1),B("path",{fill:"none",stroke:"currentColor","stroke-linecap":"round","stroke-linejoin":"round","stroke-width":"32",d:"M176 368h160"},null,-1)]))}}),gn={xmlns:"http://www.w3.org/2000/svg","xmlns:xlink":"http://www.w3.org/1999/xlink",viewBox:"0 0 512 512"},hn=L({name:"PauseOutline",render:function(s,t){return g(),R("svg",gn,t[0]||(t[0]=[B("path",{fill:"none",stroke:"currentColor","stroke-linecap":"round","stroke-linejoin":"round","stroke-width":"32",d:"M176 96h16v320h-16z"},null,-1),B("path",{fill:"none",stroke:"currentColor","stroke-linecap":"round","stroke-linejoin":"round","stroke-width":"32",d:"M320 96h16v320h-16z"},null,-1)]))}}),vn={class:"step-timeline"},bn=["onClick"],yn={key:0,class:"tl-duration"},wn=["title"],kn={key:1,class:"tl-meta"},xn={class:"subflow-label"},Sn=["title"],$n=["onClick"],Cn={key:0,class:"tl-duration"},_n=["title"],zn={key:1,class:"tl-meta"},Pn=L({__name:"StepTimeline",props:{steps:{},selectedKey:{}},emits:["select"],setup(e,{emit:s}){const t=e,p=F(new Set);function r(c){const y=new Set(p.value);y.has(c)?y.delete(c):y.add(c),p.value=y}function l(c){return p.value.has(c)}const d=s,m=qe();function i(c){const y=c;return y==="completed"?"success":y==="running"?"info":y==="failed"?"error":y==="skipped"?"warning":"default"}function u(c){return c==="pass"?"success":c==="fail"||c==="failed"?"error":"default"}function S(c){if(c==null||Number.isNaN(c))return"";const y=Math.max(0,Math.floor(c/1e3)),h=Math.floor(y/3600),j=Math.floor(y%3600/60),H=y%60;return h>0?`${h}h${String(j).padStart(2,"0")}m`:j>0?`${j}m${String(H).padStart(2,"0")}s`:`${H}s`}function o(c){if(!c)return"";const y=c.replace(/\\/g,"/").split("/");return y[y.length-1].replace(/\.md$/,"")}function v(c){const y=c.logFile;if(!y)return"";const h=y.replace(/\\/g,"/").split("/").pop()??"";return/^oc-.*\.log$/.test(h)?h:""}function w(c){return c.suspendGapMs!=null?`系统挂起（墙钟跳变约 ${Math.round(c.suspendGapMs/6e4)} min）`:"系统挂起（墙钟跳变）"}function z(c,y){return`${c.name}-${y}`}function _(c){return t.selectedKey===`p${c}`}function x(c,y,h){return t.selectedKey===`c${c}_${y}_${h}`}function $(c,y,h){d("select",{step:c,key:y,logFile:h})}async function b(c){try{await navigator.clipboard.writeText("opencode --session "+c),m.success(`Copied: opencode --session ${c.slice(0,12)}…`)}catch{m.error("Copy failed — clipboard unavailable")}}return(c,y)=>(g(),R("div",vn,[e.steps.length===0?(g(),I(n(ne),{key:0,description:"No steps yet.",size:"small"})):(g(),I(n(Ne),{key:1,size:"medium"},{default:k(()=>[(g(!0),R(le,null,ue(e.steps,(h,j)=>{var H;return g(),I(n(Be),{key:z(h,j),type:i(h.status),class:Ce(["tl-item",{selected:_(j),suspended:h.suspended}])},We({header:k(()=>{var W;return[B("span",{class:"tl-title",onClick:Z(U=>$(h,`p${j}`,v(h)),["stop"])},P(h.name),9,bn),h.durationMs!=null?(g(),R("span",yn,P(S(h.durationMs)),1)):O("",!0),h.marker?(g(),I(n(ee),{key:1,type:u(h.marker),size:"tiny",round:"",class:"marker-tag",title:h.error||void 0},{default:k(()=>[E(P(h.marker),1)]),_:2},1032,["type","title"])):O("",!0),h.suspended?(g(),I(n(ee),{key:2,type:"warning",size:"tiny",round:"",title:w(h)},{default:k(()=>[...y[1]||(y[1]=[E(" ⚠ suspended ",-1)])]),_:1},8,["title"])):O("",!0),h.type==="subflow"&&((W=h.children)!=null&&W.length)?(g(),I(n(K),{key:3,size:"tiny",quaternary:"",class:"collapse-toggle",onClick:Z(U=>r(j),["stop"])},{icon:k(()=>[C(n(X),{component:l(j)?n(cn):n(et)},null,8,["component"])]),default:k(()=>[E(" "+P(h.children.length)+" sub ",1)]),_:2},1032,["onClick"])):O("",!0)]}),footer:k(()=>[h.error?(g(),R("span",{key:0,class:"tl-error",title:h.error},"⚠ "+P(h.error.slice(0,120)),9,wn)):O("",!0),h.visits!=null&&h.visits>1?(g(),R("span",kn,"visit "+P(h.visits),1)):O("",!0),v(h)?(g(),I(n(K),{key:2,size:"tiny",quaternary:"",class:"session-copy",title:"有日志 — 点击查看",onClick:Z(W=>$(h,`p${j}`,v(h)),["stop"])},{icon:k(()=>[C(n(X),{component:n(Te)},null,8,["component"])]),default:k(()=>[y[2]||(y[2]=E(" log ",-1))]),_:1},8,["onClick"])):O("",!0),h.sessionId?(g(),I(n(K),{key:3,size:"tiny",quaternary:"",class:"session-copy",title:"复制 opencode 跟踪命令",onClick:Z(W=>b(h.sessionId),["stop"])},{icon:k(()=>[C(n(X),{component:n(Q)},null,8,["component"])]),default:k(()=>[y[3]||(y[3]=E(" session ",-1))]),_:1},8,["onClick"])):O("",!0)]),_:2},[h.type==="subflow"&&((H=h.children)!=null&&H.length)?{name:"default",fn:k(()=>[Ve(B("div",{class:"subflow-children",onClick:y[0]||(y[0]=Z(()=>{},["stop"]))},[(g(!0),R(le,null,ue(h.children,(W,U)=>(g(),R("div",{key:`${h.name}-child-${U}`,class:"subflow-group"},[B("div",xn,[h.forEach||W.forEachItem?(g(),R("span",{key:0,title:W.forEachItem??""}," 📋 Plan "+P(W.forEachIndex+1)+P(W.forEachItem?`: ${o(W.forEachItem)}`:""),9,Sn)):O("",!0),C(n(ee),{type:i(W.status),size:"tiny",round:""},{default:k(()=>[E(P(W.status),1)]),_:2},1032,["type"])]),C(n(Ne),{class:"subflow-inner"},{default:k(()=>[(g(!0),R(le,null,ue(W.steps,(M,D)=>(g(),I(n(Be),{key:`${h.name}-${U}-${D}`,type:i(M.status),time:S(M.durationMs),class:Ce(["tl-item sub-item",{selected:x(j,U,D)}])},{header:k(()=>[B("span",{class:"tl-title",onClick:Z(N=>$(M,`c${j}_${U}_${D}`,v(M)),["stop"])},P(M.name),9,$n),M.durationMs!=null?(g(),R("span",Cn,P(S(M.durationMs)),1)):O("",!0),M.marker?(g(),I(n(ee),{key:1,type:u(M.marker),size:"tiny",round:"",class:"marker-tag",title:M.error||void 0},{default:k(()=>[E(P(M.marker),1)]),_:2},1032,["type","title"])):O("",!0)]),footer:k(()=>[M.error?(g(),R("span",{key:0,class:"tl-error",title:M.error},"⚠ "+P(M.error.slice(0,120)),9,_n)):O("",!0),M.visits!=null&&M.visits>1?(g(),R("span",zn,"visit "+P(M.visits),1)):O("",!0),v(M)?(g(),I(n(K),{key:2,size:"tiny",quaternary:"",class:"session-copy",title:"有日志 — 点击查看",onClick:Z(N=>$(M,`c${j}_${U}_${D}`,v(M)),["stop"])},{icon:k(()=>[C(n(X),{component:n(Te)},null,8,["component"])]),default:k(()=>[y[4]||(y[4]=E(" log ",-1))]),_:1},8,["onClick"])):O("",!0),M.sessionId?(g(),I(n(K),{key:3,size:"tiny",quaternary:"",class:"session-copy",title:"复制 opencode 跟踪命令",onClick:Z(N=>b(M.sessionId),["stop"])},{icon:k(()=>[C(n(X),{component:n(Q)},null,8,["component"])]),default:k(()=>[y[5]||(y[5]=E(" session ",-1))]),_:1},8,["onClick"])):O("",!0)]),_:2},1032,["type","time","class"]))),128))]),_:2},1024)]))),128))],512),[[Ge,!l(j)]])]),key:"0"}:void 0]),1032,["type","class"])}),128))]),_:1}))]))}}),Rn=te(Pn,[["__scopeId","data-v-7e86915a"]]),In={key:0,class:"commands-list"},Nn=["title"],Bn=L({__name:"MissionConfig",props:{config:{}},setup(e){const s=qe();async function t(p){if(p)try{await navigator.clipboard.writeText(p),s.success(`Copied: ${p.slice(0,24)}${p.length>24?"…":""}`)}catch{s.error("Copy failed — clipboard unavailable")}}return(p,r)=>e.config?(g(),I(n(Wt),{key:1,bordered:"",column:1,size:"small","label-placement":"left","label-style":{width:"130px",whiteSpace:"nowrap",verticalAlign:"top"},"content-style":"word-break: break-all"},{default:k(()=>[C(n(re),{label:"description"},{default:k(()=>[E(P(e.config.description||"—")+" ",1),e.config.description?(g(),I(n(K),{key:0,size:"tiny",quaternary:"",class:"cfg-copy",onClick:r[0]||(r[0]=l=>t(e.config.description))},{icon:k(()=>[C(n(X),{component:n(Q)},null,8,["component"])]),_:1})):O("",!0)]),_:1}),C(n(re),{label:"roadmapPath"},{default:k(()=>[E(P(e.config.roadmapPath||"—")+" ",1),e.config.roadmapPath?(g(),I(n(K),{key:0,size:"tiny",quaternary:"",class:"cfg-copy",onClick:r[1]||(r[1]=l=>t(e.config.roadmapPath))},{icon:k(()=>[C(n(X),{component:n(Q)},null,8,["component"])]),_:1})):O("",!0)]),_:1}),C(n(re),{label:"plansDir"},{default:k(()=>[E(P(e.config.plansDir||"—")+" ",1),e.config.plansDir?(g(),I(n(K),{key:0,size:"tiny",quaternary:"",class:"cfg-copy",onClick:r[2]||(r[2]=l=>t(e.config.plansDir))},{icon:k(()=>[C(n(X),{component:n(Q)},null,8,["component"])]),_:1})):O("",!0)]),_:1}),C(n(re),{label:"moduleDir"},{default:k(()=>[E(P(e.config.moduleDir||"—")+" ",1),e.config.moduleDir?(g(),I(n(K),{key:0,size:"tiny",quaternary:"",class:"cfg-copy",onClick:r[3]||(r[3]=l=>t(e.config.moduleDir))},{icon:k(()=>[C(n(X),{component:n(Q)},null,8,["component"])]),_:1})):O("",!0)]),_:1}),C(n(re),{label:"flowName"},{default:k(()=>[E(P(e.config.flowName||"—")+" ",1),e.config.flowName?(g(),I(n(K),{key:0,size:"tiny",quaternary:"",class:"cfg-copy",onClick:r[4]||(r[4]=l=>t(e.config.flowName))},{icon:k(()=>[C(n(X),{component:n(Q)},null,8,["component"])]),_:1})):O("",!0)]),_:1}),C(n(re),{label:"commitFormat"},{default:k(()=>[E(P(e.config.commitFormat||"—")+" ",1),e.config.commitFormat?(g(),I(n(K),{key:0,size:"tiny",quaternary:"",class:"cfg-copy",onClick:r[5]||(r[5]=l=>t(e.config.commitFormat))},{icon:k(()=>[C(n(X),{component:n(Q)},null,8,["component"])]),_:1})):O("",!0)]),_:1}),C(n(re),{label:"commands",span:1},{default:k(()=>[e.config.commands&&Object.keys(e.config.commands).length?(g(),R("div",In,[(g(!0),R(le,null,ue(e.config.commands,(l,d)=>(g(),R("div",{key:d,class:"command-row"},[C(n(ee),{size:"tiny",round:""},{default:k(()=>[E(P(d),1)]),_:2},1024),B("span",{class:"cmd-val",title:l??""},P(l||"—"),9,Nn),l?(g(),I(n(K),{key:0,size:"tiny",quaternary:"",onClick:m=>t(l)},{icon:k(()=>[C(n(X),{component:n(Q)},null,8,["component"])]),_:1},8,["onClick"])):O("",!0)]))),128))])):(g(),I(n(pe),{key:1,depth:"3"},{default:k(()=>[...r[6]||(r[6]=[E("—",-1)])]),_:1}))]),_:1})]),_:1})):(g(),I(n(ne),{key:0,description:"No mission config.",size:"small"}))}}),Tn=te(Bn,[["__scopeId","data-v-362274c4"]]),Mn=L({__name:"PlansTable",props:{plans:{},plansDir:{}},setup(e){const s=e;function t(i){return i==="active"?"info":i==="completed"?"success":i==="draft"||i==="planned"?"default":i==="failed"?"error":"default"}function p(i){return i==null||Number.isNaN(i)?"—":i<1024?`${i} B`:i<1024*1024?`${(i/1024).toFixed(1)} KB`:`${(i/(1024*1024)).toFixed(1)} MB`}function r(i){if(i==null||Number.isNaN(i))return"—";const u=new Date(i);return Number.isNaN(u.getTime())?"—":u.toLocaleString()}function l(i){return i.replace(/\\/g,"/").split("/").slice(-2).join("/")}const d=T(()=>[{title:"File",key:"fileName"},{title:"Status",key:"status",render:i=>a(ee,{type:t(i.status),size:"tiny",round:!0},{default:()=>i.status})},{title:"Size",key:"sizeBytes",render:i=>p(i.sizeBytes)},{title:"Last Modified",key:"lastModified",render:i=>r(i.lastModified)}]),m=T(()=>s.plans);return(i,u)=>(g(),I(n(ae),{title:"Plans",size:"small"},We({default:k(()=>[e.plans.length===0?(g(),I(n(ne),{key:0,description:"No plan files found.",size:"small"})):(g(),I(n(ye),{key:1,columns:d.value,data:m.value,"row-key":S=>S.fileName,size:"small",striped:"",bordered:""},null,8,["columns","data","row-key"]))]),_:2},[e.plansDir?{name:"header-extra",fn:k(()=>[C(n(pe),{depth:"3",class:"plans-dir",title:e.plansDir},{default:k(()=>[E(P(l(e.plansDir)),1)]),_:1},8,["title"])]),key:"0"}:void 0]),1024))}}),Dn=te(Mn,[["__scopeId","data-v-5cacfe66"]]),jn={key:1,class:"roadmap-progress"},On={class:"overall-row"},An={class:"phase-list"},En=["title"],Fn={key:0,class:"phase-seq"},Ln=L({__name:"RoadmapProgress",setup(e){const s=ve(),t=T(()=>s.roadmap.phases),p=T(()=>{const u=t.value.filter(w=>!w.isMilestone),S=u.filter(w=>w.status==="done").length,o=u.length,v=o>0?Math.round(S/o*100):0;return`${S}/${o} done · ${v}%`}),r=T(()=>Math.round((s.roadmap.overallProgress??0)*100)),l=T(()=>{const u=t.value.filter(o=>!o.isMilestone);return u.filter(o=>o.status==="done").length===u.length&&u.length>0?"success":"default"});function d(u){return u.seq!=null?`${u.seq}.`:"·"}function m(u){switch(u.status){case"done":return"success";case"ready":case"planned":return"info";case"not-done":return"warning";default:return"default"}}function i(u){return u.isMilestone?u.status==="done"?"★ done":"★ "+(u.status==="not-done"?"未达成":u.status):(u.status==="done"?"✓ ":u.status==="todo"?"○ ":"")+u.status}return(u,S)=>(g(),I(n(ae),{title:"Roadmap Progress",size:"small"},{default:k(()=>[t.value.length===0?(g(),I(n(ne),{key:0,description:"暂无 roadmap 数据",size:"small"})):(g(),R("div",jn,[B("div",On,[C(n(en),{type:"line",percentage:r.value,"indicator-placement":"inside",height:20,"border-radius":4},null,8,["percentage"]),C(n(ee),{type:l.value,round:"",size:"small",class:"overall-pill"},{default:k(()=>[E(P(p.value),1)]),_:1},8,["type"])]),B("div",An,[(g(!0),R(le,null,ue(t.value,(o,v)=>(g(),R("div",{key:"phase-"+v+"-"+o.name,class:"phase-row"},[B("div",{class:"phase-name",title:o.name},[o.isMilestone?O("",!0):(g(),R("span",Fn,P(d(o)),1)),B("span",null,P(o.name),1)],8,En),C(n(ee),{type:m(o),size:"tiny",round:"",bordered:!1},{default:k(()=>[E(P(i(o)),1)]),_:2},1032,["type"])]))),128))])]))]),_:1}))}}),qn=te(Ln,[["__scopeId","data-v-3ad06ec4"]]);function Wn(e,s){let t=null;function p(){if(!t){t=new EventSource(e);for(const l of Object.keys(s)){const d=s[l];d&&t.addEventListener(l,m=>{try{d(JSON.parse(m.data))}catch{}})}}}function r(){t&&(t.close(),t=null)}return bt(r),{connect:p,disconnect:r}}const xe=Ke("sysmon",()=>{const e=F([]),s=T(()=>{const r=e.value;return r.length>0?r[r.length-1]:null});function t(r){e.value.push(r)}async function p(r){try{const{snapshots:l}=await Pt(r);e.value=l}catch{}}return{snapshots:e,latest:s,fetch:p,append:t}});let de=null;function Vn(e){if(e.status)return e.status;switch(e.type){case"step_started":return"running";case"step_completed":return"completed";case"step_failed":return"failed";case"step_skipped":return"skipped";default:return"pending"}}const fe=Ke("mission",()=>{const e=F(null),s=F(null),t=F([]),p=F(null),r=F(null),l=F(null),d=F([]),m=F(null);function i(x){s.value=x,t.value=Array.isArray(x.steps)?x.steps:[]}function u(x){var b;if(x.config)return x;const $=((b=s.value)==null?void 0:b.config)??null;return{...x,config:$}}async function S(x){e.value=x,m.value=null;try{const $=await Rt(x);i({...$.run,config:$.config}),d.value=$.stepLogs??[]}catch($){m.value=$ instanceof Error?$.message:String($)}}function o(x){if(!x.step)return;const $=x.visit??1,b=t.value.findIndex(y=>y.name===x.step&&(y.visits??1)===$),c={name:x.step,status:Vn(x),visits:$};x.marker!=null&&(c.marker=x.marker),x.durationMs!=null&&(c.durationMs=x.durationMs),x.startedAt!=null&&(c.startedAt=x.startedAt),x.endedAt!=null&&(c.endedAt=x.endedAt),x.error!=null&&(c.error=x.error),x.sessionId!=null&&(c.sessionId=x.sessionId),x.promptFile!=null&&(c.promptFile=x.promptFile),b>=0?t.value[b]={...t.value[b],...c}:t.value.push(c)}function v(x,$,b){p.value=x,r.value=$,l.value=b}function w(x){z(),e.value=x;const $=ve(),b=xe();de=Wn(`/api/runs/${encodeURIComponent(x)}/events`,{snapshot:c=>i(u(c)),state_update:c=>i(u(c)),step_started:c=>o(c),step_completed:c=>{var h;o(c);const y=(h=s.value)==null?void 0:h.missionName;y&&$.fetchRoadmap(y)},step_failed:c=>o(c),heartbeat:()=>{b.fetch(x)},run_completed:c=>{s.value&&c.status&&(s.value={...s.value,status:c.status})},error:c=>{m.value=c.message||"SSE error"}}),de.connect()}function z(){de&&(de.disconnect(),de=null)}function _(){z(),e.value=null,s.value=null,t.value=[],p.value=null,r.value=null,l.value=null,d.value=[],m.value=null}return{currentRunId:e,currentRun:s,steps:t,selectedStep:p,selectedStepKey:r,selectedLogFile:l,stepLogs:d,errorMsg:m,loadRun:S,connectSSE:w,disconnectSSE:z,upsertStep:o,selectStep:v,clear:_}}),Gn={key:0,class:"modal-loading"},Kn={key:2,class:"prompt-body"},Hn={class:"meta-row"},Un=L({__name:"PromptModal",props:{show:{type:Boolean},runId:{}},emits:["update:show"],setup(e){const s=e,t=fe(),p=F(null),r=F(!1),l=F(!1),d=F(""),m=F(0);let i=null,u=null,S=!1;const o={theme:{background:"#1e1e1e",foreground:"#d4d4d4",cursor:"#d4d4d4"},fontFamily:"ui-monospace, Menlo, Consolas, monospace",fontSize:13,scrollback:1e4,disableStdin:!0,convertEol:!1};async function v(){var c;const $=t.selectedStep;if(!$)return;r.value=!0,l.value=!1;let b;try{const y=t.selectedLogFile??void 0,h=await It(s.runId,$,{file:y});d.value=h.fileName??"",b=(c=h.lines)!=null&&c.length?h.lines.join(`\r
`):"(empty)",m.value=b.length}catch{l.value=!0,d.value="",m.value=0,r.value=!1;return}r.value=!1,await be(),w(),i==null||i.write(b),S=!0}function w(){if(!(!p.value||i)){i=new Ye.Terminal(o),u=new Je.FitAddon,i.loadAddon(u),i.open(p.value),i.attachCustomKeyEventHandler($=>($.ctrlKey||$.metaKey)&&$.key==="c"&&i.hasSelection()?(navigator.clipboard.writeText(i.getSelection()),!1):!0);try{u.fit()}catch{}}}function z(){i==null||i.dispose(),i=null,u=null}function _(){S?be(()=>{try{u==null||u.fit()}catch{}}):v()}function x(){z(),S=!1}return ke(()=>t.selectedStep,()=>{s.show&&(z(),S=!1,l.value=!1,d.value="",m.value=0,v())}),($,b)=>(g(),I(n(yt),{show:e.show,"mask-closable":!0,"onUpdate:show":b[1]||(b[1]=c=>$.$emit("update:show",c)),onAfterEnter:_,onAfterLeave:x},{default:k(()=>[C(n(ae),{size:"small",closable:"",style:{width:"min(960px, 95vw)"},onClose:b[0]||(b[0]=c=>$.$emit("update:show",!1))},{header:k(()=>[...b[2]||(b[2]=[B("span",{class:"modal-header-title"},"Agent Prompt",-1)])]),default:k(()=>[r.value?(g(),R("div",Gn,[C(n(He))])):l.value?(g(),I(n(ne),{key:1,description:"No prompt available for this step.",size:"small"})):(g(),R("div",Kn,[B("div",Hn,[C(n(pe),{depth:"3",class:"meta-file"},{default:k(()=>[E(P(d.value),1)]),_:1}),C(n(pe),{depth:"3"},{default:k(()=>[E(P(m.value)+" chars",1)]),_:1})]),B("div",{ref_key:"promptContainer",ref:p,class:"prompt-term-container"},null,512)]))]),_:1})]),_:1},8,["show"]))}}),Xn=te(Un,[["__scopeId","data-v-4fc171db"]]),Yn={class:"log-viewer"},Jn={key:0,class:"log-info"},Zn=["title"],Qn={class:"log-linecount"},eo={key:1,class:"log-toolbar"},to={class:"log-toolbar-right"},no={key:2,class:"load-more-row"},he=500,oo=500,Me=3e3,ro=L({__name:"LogViewer",props:{runId:{}},setup(e){const s=e,t=fe(),p=F(null),r=F(""),l=F(!1),d=F(0),m=F(0),i=F(!1),u=F(""),S=F(!1),o=F(!1),v=T(()=>{const D=t.selectedStep;return D?t.stepLogs.some(N=>N.step===D&&N.type==="prompt")?!0:t.steps.some(N=>N.name===D&&!!N.promptFile):!1});let w=null,z=null,_=null,x=null,$=null,b=he;function c(D){const N={tail:D},q=t.selectedLogFile;return q&&(N.file=q),N}async function y(D){var se,ge;const N=t.selectedStep;if(!N||!w)return;const q=D??b;try{const Y=await _e(s.runId,N,c(q));d.value=Y.totalLines??0,m.value=((se=Y.lines)==null?void 0:se.length)??0,i.value=!!Y.truncated,u.value=Y.fileName??"";const oe=(ge=Y.lines)!=null&&ge.length?Y.lines.join(`\r
`):"(empty log)";w.reset(),w.write(oe)}catch(Y){const oe=Y instanceof Error?Y.message:String(Y);w&&(w.reset(),oe.includes("404")?(w.write("无日志"),u.value=""):w.write(`Failed to load log: ${oe}`)),d.value=0,m.value=0,i.value=!1}}function h(){!_||!r.value||_.findNext(r.value)}function j(){!_||!r.value||_.findPrevious(r.value)}function H(){b+=oo,y()}async function W(){var N;const D=t.selectedStep;if(D)try{const q=await _e(s.runId,D,c(99999)),se=(N=q.lines)!=null&&N.length?q.lines.join(`
`):"(empty)",ge=new Blob([se],{type:"text/plain;charset=utf-8"}),Y=URL.createObjectURL(ge),oe=window.open(Y,"_blank");oe&&(oe.document.title=q.fileName??D)}catch(q){const se=q instanceof Error?q.message:String(q);console.error("openFullLog failed:",se)}}function U(){S.value=!S.value,S.value?$=setInterval(()=>{y(he).then(()=>{w==null||w.scrollToBottom()})},Me):$&&(clearInterval($),$=null)}function M(){if(!(!p.value||w)){w=new Ye.Terminal({theme:{background:"#1e1e1e",foreground:"#d4d4d4",cursor:"#d4d4d4"},fontFamily:"ui-monospace, Menlo, Consolas, monospace",fontSize:13,scrollback:1e4,disableStdin:!0,convertEol:!1}),z=new Je.FitAddon,_=new Tt.SearchAddon,w.loadAddon(z),w.loadAddon(_),w.open(p.value),w.attachCustomKeyEventHandler(D=>(D.ctrlKey||D.metaKey)&&D.key==="c"&&w.hasSelection()?(navigator.clipboard.writeText(w.getSelection()),!1):!0);try{z.fit()}catch{}l.value=!0,x=new ResizeObserver(()=>{try{z==null||z.fit()}catch{}}),x.observe(p.value)}}return Ue(()=>{M(),t.selectedStep&&y()}),Xe(()=>{$&&(clearInterval($),$=null),x==null||x.disconnect(),w==null||w.dispose(),w=null,z=null,_=null,x=null}),ke([()=>t.selectedStep,()=>t.selectedStepKey,()=>t.selectedLogFile],async()=>{t.selectedStep&&!w&&(await be(),M()),b=he,$&&(clearInterval($),$=null);const D=t.selectedStep,N=D?t.steps.some(q=>q.name===D&&q.status==="running"):!1;t.selectedStep&&w&&y(),S.value=N,N&&($=setInterval(()=>{y(he).then(()=>{w==null||w.scrollToBottom()})},Me))}),(D,N)=>(g(),R("div",Yn,[n(t).selectedStep?(g(),R(le,{key:0},[l.value&&u.value?(g(),R("div",Jn,[B("span",{class:"log-filepath",title:`点击在新标签页打开完整日志
`+u.value,onClick:W},P(u.value),9,Zn),B("span",Qn,P(d.value)+" lines",1)])):O("",!0),l.value?(g(),R("div",eo,[C(n(K),{size:"small",type:S.value?"primary":"default",onClick:U},{icon:k(()=>[C(n(X),{component:S.value?n(hn):n(sn)},null,8,["component"])]),default:k(()=>[E(" "+P(S.value?"停止跟踪":"跟踪最新"),1)]),_:1},8,["type"]),B("div",to,[C(n(K),{size:"small",disabled:!v.value,onClick:N[0]||(N[0]=q=>o.value=!0)},{default:k(()=>[...N[3]||(N[3]=[E(" Show Prompt ",-1)])]),_:1},8,["disabled"]),C(n(Et),null,{default:k(()=>[C(n(Nt),{value:r.value,"onUpdate:value":N[1]||(N[1]=q=>r.value=q),size:"small",placeholder:"search…",onKeydown:wt(Z(h,["prevent"]),["enter"])},null,8,["value","onKeydown"]),C(n(K),{size:"small",title:"查找下一个",onClick:h},{icon:k(()=>[C(n(X),{component:n(et)},null,8,["component"])]),_:1}),C(n(K),{size:"small",title:"查找上一个",onClick:j},{icon:k(()=>[C(n(X),{component:n(mn)},null,8,["component"])]),_:1})]),_:1})])])):O("",!0),B("div",{ref_key:"containerEl",ref:p,class:"term-container"},null,512),i.value?(g(),R("div",no,[C(n(K),{size:"small",onClick:H},{default:k(()=>[E(" 加载更多日志 (已加载 "+P(m.value)+"/"+P(d.value)+") ",1)]),_:1})])):O("",!0)],64)):(g(),I(n(ne),{key:1,description:"Select a step from the timeline to view its log.",size:"small"})),C(Xn,{show:o.value,"onUpdate:show":N[2]||(N[2]=q=>o.value=q),"run-id":e.runId},null,8,["show","run-id"])]))}}),io=te(ro,[["__scopeId","data-v-1dd5606e"]]),so={class:"resource-chart"},lo={key:0,class:"hist-section"},ao={class:"proc-head"},co={class:"proc-meta"},uo={key:2,class:"proc-section"},po={class:"proc-head"},mo={key:0,class:"proc-meta"},fo=L({__name:"ResourceChart",setup(e){const s=xe(),t=fe(),p=T(()=>s.snapshots.length>0),r=T(()=>s.snapshots.slice(-8).reverse());function l(o){if(!o)return"—";const v=new Date(o);return Number.isNaN(v.getTime())?o:v.toLocaleTimeString(void 0,{hour12:!1})}const d=T(()=>[{title:"Time",key:"ts",width:90,render:o=>a("span",{class:"mono-sm"},l(o.ts))},{title:"Free (GB)",key:"freeGB",width:80,render:o=>a("span",{class:"mono-sm"},o.freeGB!=null?o.freeGB.toFixed(2):"—")},{title:"OC RSS (GB)",key:"opencodeRSS_MB",width:90,render:o=>a("span",{class:"mono-sm"},o.opencodeRSS_MB!=null?(o.opencodeRSS_MB/1024).toFixed(2):"—")},{title:"OC",key:"opencodeCount",width:50,render:o=>a("span",{class:"mono-sm"},String(o.opencodeCount??"—"))},{title:"Node",key:"nodeCount",width:56,render:o=>a("span",{class:"mono-sm"},String(o.nodeCount??"—"))},{title:"Pressure",key:"memPressure",width:80,render:o=>o.memPressure??"—"}]),m=T(()=>{var o;return((o=s.latest)==null?void 0:o.topProcs)??[]}),i=T(()=>{var o;return((o=t.currentRun)==null?void 0:o.pid)??null}),u=T(()=>{var v;const o=(v=t.currentRun)==null?void 0:v.status;return!(o==="completed"||o==="aborted"||i.value==null)}),S=T(()=>[{title:"PID",key:"pid",width:70,render:o=>a("span",{class:"mono-sm"},String(o.pid))},{title:"Process",key:"name",render:o=>[a("span",{class:"proc-name"},o.name),o.name.match(/opencode/i)?a(ee,{size:"tiny",round:!0,type:"info",style:"margin-left:6px"},{default:()=>"opencode"}):null]},{title:"RSS",key:"rss_mb",width:80,render:o=>a("span",{class:"mono-sm"},`${o.rss_mb} MB`)},{title:"CPU",key:"cpu_pct",width:60,render:o=>o.cpu_pct!=null?a("span",{class:"mono-sm"},`${o.cpu_pct}%`):"—"},{title:"Elapsed",key:"elapsed",width:80,render:o=>o.elapsed||"—"}]);return(o,v)=>(g(),R("div",so,[p.value?(g(),R("div",lo,[B("div",ao,[v[0]||(v[0]=B("span",{class:"proc-title"},"Resource History",-1)),B("span",co,"最近 "+P(r.value.length)+" 条",1)]),C(n(ye),{columns:d.value,data:r.value,"row-key":w=>w.ts??"",size:"small",bordered:!1,"single-line":!1},null,8,["columns","data","row-key"])])):(g(),I(n(ne),{key:1,description:"暂无资源监控数据",size:"small",class:"empty"})),u.value&&m.value.length>0?(g(),R("div",uo,[B("div",po,[v[1]||(v[1]=B("span",{class:"proc-title"},"Active Processes",-1)),n(s).latest?(g(),R("span",mo,P(n(s).latest.opencodeCount??0)+" opencode · "+P(n(s).latest.nodeCount??0)+" node · "+P(n(s).latest.memPressure??"—"),1)):O("",!0)]),C(n(ye),{columns:S.value,data:m.value,"row-key":w=>w.pid,size:"small",bordered:!1,"single-line":!1},null,8,["columns","data","row-key"])])):O("",!0)]))}}),go=te(fo,[["__scopeId","data-v-971af275"]]),ho={class:"pane-card"},vo={class:"pane-card"},bo=L({__name:"DefaultRunDetail",props:{runId:{},run:{}},setup(e){const s=fe(),t=ve(),p=F(!1);function r(l){s.selectStep(l.step.name,l.key,l.logFile)}return(l,d)=>(g(),I(n(xt),{"has-sider":"",class:"detail-layout"},{default:k(()=>[C(n(Mt),{bordered:"",width:320,"content-style":"padding: 12px; height: calc(100vh - 120px); overflow: auto"},{default:k(()=>[C(n(pe),{depth:"2",class:"pane-title"},{default:k(()=>[...d[1]||(d[1]=[E("Step Timeline",-1)])]),_:1}),n(s).steps.length>0?(g(),I(Rn,{key:0,steps:n(s).steps,"selected-key":n(s).selectedStepKey,onSelect:r},null,8,["steps","selected-key"])):(g(),I(n(ne),{key:1,description:"No steps yet.",size:"small"}))]),_:1}),C(n(kt),{"content-style":"padding: 12px 16px; height: calc(100vh - 120px); overflow: auto"},{default:k(()=>[C(n(ae),{title:"Mission Config",size:"small",class:"pane-card"},{"header-extra":k(()=>[C(n(K),{size:"tiny",text:"",onClick:d[0]||(d[0]=m=>p.value=!p.value)},{default:k(()=>[C(n(X),{component:p.value?n(un):n(Dt)},null,8,["component"])]),_:1})]),default:k(()=>{var m;return[Ve(C(Tn,{config:((m=n(s).currentRun)==null?void 0:m.config)??null},null,8,["config"]),[[Ge,p.value]])]}),_:1}),C(n(ae),{title:"Log Viewer",size:"small",class:"pane-card"},{default:k(()=>[C(io,{"run-id":e.runId},null,8,["run-id"])]),_:1}),C(n(ae),{title:"Resource Monitor",size:"small",class:"pane-card"},{default:k(()=>[C(go)]),_:1}),B("div",ho,[C(Dn,{plans:n(t).plans,"plans-dir":n(t).plansDir},null,8,["plans","plans-dir"])]),B("div",vo,[C(qn)])]),_:1})]),_:1}))}}),De=te(bo,[["__scopeId","data-v-7ad2f714"]]),yo={},je=new Map;function wo(e){if(!e)return De;const s=yo[e];if(!s)return De;let t=je.get(e);return t||(t=St(s),je.set(e,t)),t}const ko={class:"run-detail"},xo=L({__name:"RunDetail",setup(e){const s=_t(),t=fe(),p=xe(),r=ve(),l=F(!1),d=T(()=>typeof s.params.runId=="string"?s.params.runId:""),m=T(()=>t.currentRun);async function i(S){var o;l.value=!0;try{await t.loadRun(S),t.connectSSE(S),p.fetch(S);const v=(o=t.currentRun)==null?void 0:o.missionName;v&&r.fetchAllConfig(v)}finally{l.value=!1}}function u(){t.disconnectSSE(),t.clear()}return Ue(()=>{d.value&&i(d.value)}),Xe(u),ke(d,(S,o)=>{S!==o&&(u(),S&&i(S))}),(S,o)=>{var v,w,z,_;return g(),R("div",ko,[C(Bt,{"show-back":"",title:((v=n(t).currentRun)==null?void 0:v.missionName)??"Loading run…",status:((w=n(t).currentRun)==null?void 0:w.status)??null,"started-at":((z=n(t).currentRun)==null?void 0:z.startedAt)??null,"mission-name":((_=n(t).currentRun)==null?void 0:_.missionName)??null},null,8,["title","status","started-at","mission-name"]),n(t).errorMsg?(g(),I(n($t),{key:0,type:"error",title:n(t).errorMsg,style:{"margin-bottom":"12px"}},null,8,["title"])):O("",!0),C(n(He),{show:l.value&&!n(t).currentRun},{default:k(()=>{var x;return[(g(),I(Ct(n(wo)((x=m.value)==null?void 0:x.flowName)),{"run-id":d.value,run:m.value},null,8,["run-id","run"]))]}),_:1},8,["show"])])}}}),Po=te(xo,[["__scopeId","data-v-9b383306"]]);export{Po as default};
