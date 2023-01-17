System.register(['@nop/utils', './sub.lib.js'], (function (exports) {
    'use strict';
    var ajaxFetch, ajaxRequest, myAction;
    return {
        setters: [module => {
            ajaxFetch = module.ajaxFetch;
            ajaxRequest = module.ajaxRequest;
        }, module => {
            myAction = module.myAction;
        }],
        execute: (function () {

            exports('testAction', testAction);

            function myAction2(options, page){
                page.env.alert("in sub2 lib");
            }

            function testAction(options, page){
            page.env.alert("xx");
            ajaxFetch(options);
            ajaxRequest(options);
            myAction(options,page);
            myAction2(options,page);
            return Promise.resolve({
            status: 200 ,
            data: {
            status: 0
            }
            })
            }

        })
    };
}));
