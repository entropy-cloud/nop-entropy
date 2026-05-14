System.register(['@nop-chaos/sdk','vue','react'], (function (exports) {
	'use strict';
	var sdk, Vue, React;
	return {
		setters: [module => {
			sdk = module;
		}, module => {
			Vue = module;
		}, module =>{
		    React = module
		}],
		execute: (function () {
			const componentType = sdk.defineReactPageComponent(()=>{
				return {
				  getComponent(name) {
						
				  },
			  
				  getScopedStore(name) {
					
				  },
			  
				  getState(name) {
					
				  },
			  
				  setState(name, value) {
					
				  },
			  
				  onDestroyPage(page) {
					
				  },
			  
				  async onRenderPage(schema, data, page) {
					return React.createElement('div',null, JSON.stringify(schema))
				  }
				}
			})

			sdk.registerSchemaProcessorType('plain',{
				componentType,
				editorComponentType: componentType
			})
		})
	};
}));