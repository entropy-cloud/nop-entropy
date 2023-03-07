System.register(['@nop/utils', '/api/other.lib.js'], (function (exports) {
    'use strict';
    var ajaxFetch, ajaxRequest, otherAction;
    return {
        setters: [module => {
            ajaxFetch = module.ajaxFetch;
            ajaxRequest = module.ajaxRequest;
        }, module => {
            otherAction = module.otherAction;
        }],
        execute: (function () {

            exports('testAction', testAction);

            window.myVar = 1;

            function myAction(){
                alert('x');
            }

            function testAction(options, page){
                page.env.alert("xx");
                myAction(options,page);

                 ajaxFetch(options,page);
                 ajaxRequest(options,page);
                 otherAction();


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
