//generate from /nop/wf/designer/dingflow.lib.xjs
System.register(['react', '@nop-chaos/plugin-dingflow'], (function (exports) {
  'use strict';
  var createElement, Fragment, NodeTitleShell, NodeContent, ContentPlaceholder, NodeTitle, ConditionNodeTitle;
  return {
    setters: [module => {
      createElement = module.createElement;
      Fragment = module.Fragment;
    }, module => {
      NodeTitleShell = module.NodeTitleShell;
      NodeContent = module.NodeContent;
      ContentPlaceholder = module.ContentPlaceholder;
      NodeTitle = module.NodeTitle;
      ConditionNodeTitle = module.ConditionNodeTitle;
    }],
    execute: (function () {

      function Node_start(props){
                      const {node, material, parent, index,t,editable} = props;
                      
                     return createElement(Fragment,
        null,
        [
          createElement(NodeTitleShell,
            {
              className:"node-title start-node-title",
              style:{"backgroundColor" : material.color},
            },
            "\r\n                    "+t(material.label || "")+"\r\n                "
          ),
          createElement(NodeContent,
            {
              className:"content",
            },
            [
              createElement(ContentPlaceholder,
                {
                  text:t(material.info),
                },
                null
              ),
              createElement("i",
                {
                  className:"fas fa-angle-right arrow",
                },
                null
              ),
            ]
          ),
        ]
      )
                  }
              
                  function Node_end(props){
                      const {node, material, parent, index,t,editable} = props;
                      
                     return createElement(Fragment,
        null,
        [
          createElement("div",
            {
              className:"end-node-circle",
            },
            null
          ),
          createElement("div",
            {
              className:"end-node-text",
            },
            t("finish")
          ),
        ]
      )
                  }
              
                  function Node_approver(props){
                      const {node, material, parent, index,t,editable} = props;
                      
                     return createElement(Fragment,
        null,
        [
          createElement(NodeTitle,
            {
              node:node,
              material:material,
              closable:editable && material.deletable,
              editable:editable,
            },
            null
          ),
          createElement(NodeContent,
            {
              className:"content",
            },
            [
              createElement(ContentPlaceholder,
                {
                  text:t(material.info),
                },
                null
              ),
              createElement("i",
                {
                  className:"fas fa-angle-right arrow",
                },
                null
              ),
            ]
          ),
        ]
      )
                  }
              
                  function Node_condition(props){
                      const {node, material, parent, index,t,editable} = props;
                      
                     return createElement(Fragment,
        null,
        [
          createElement(ConditionNodeTitle,
            {
              node:node,
              parent:parent,
              index:index,
              editable:editable,
            },
            null
          ),
          createElement(NodeContent,
            {
              className:"content",
            },
            [
              createElement(ContentPlaceholder,
                {
                  text:t("pleaseSetCondition"),
                },
                null
              ),
            ]
          ),
        ]
      )
                  }
              
                  const Node_notifier = Node_approver;
              
                  const Node_audit = Node_approver;
              

          const NODE_COMPONENTS = exports('default', {
              
                  "start": Node_start,
              
                  "end": Node_end,
              
                  "approver": Node_approver,
              
                  "notifier": Node_notifier,
              
                  "audit": Node_audit,
              
                  "condition": Node_condition,
              
          });

    })
  };
}));
