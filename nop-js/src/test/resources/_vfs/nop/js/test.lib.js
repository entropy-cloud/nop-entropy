import { ajaxFetch} from '@nop/utils'

import {myAction} from './parts/sub.lib.js'

import { ajaxRequest} from '@nop/utils'

import {otherAction} from '/api/other.lib.js'

export function testAction(options, page){
    page.env.alert("xx");
    myAction(options,page)

     ajaxFetch(options,page)
     ajaxRequest(options,page)
     otherAction()


    return Promise.resolve({
        status: 200 ,
        data: {
            status: 0
        }
    })
}