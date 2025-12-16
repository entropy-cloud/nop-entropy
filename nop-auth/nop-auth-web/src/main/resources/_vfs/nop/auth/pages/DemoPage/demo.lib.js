//generate from /nop/auth/pages/DemoPage/demo.lib.xjs
System.register(['@nop-chaos/sdk', './sub.lib.js'], (function (exports) {
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
            exports('handlePageAction', handlePageAction)

            function handlePageAction(args, page){
              page.env.alert('pageAction:'+args.a)
            }

            function myAction2(options, page){
                page.env.alert("in sub2 lib");
            }

            function testAction(options, page){
            page.env.alert("xx");
            ajaxFetch({url:'/r/NopAuthUser__findPage'});
           // myAction(options,page);
           // myAction2(options,page);
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
