export {rollupTransform } from './libs/nop-server-tool.mjs'

export function test_map(s){
    console.log('s='+s)
    return {
        a: 1,
        b: {
            c:[1,2,'a']
        }
    }
}