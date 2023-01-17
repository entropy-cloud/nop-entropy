System.register([], (function (exports) {
	'use strict';
	return {
		execute: (function () {

			exports('myAction', myAction);

			function myAction(options, page){
			page.env.alert("in sub lib");
			}

		})
	};
}));
