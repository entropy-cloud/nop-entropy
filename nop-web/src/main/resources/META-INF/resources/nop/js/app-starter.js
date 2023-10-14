System.register(['@nop-chaos/sdk','vue','react'], (function (exports) {
	'use strict';
	var sdk, vue, react;
	return {
		setters: [module => {
			sdk = module;
		}, module => {
			vue = module;
		}, module =>{
		    react = module
		}],
		execute: (function () {

		})
	};
}));