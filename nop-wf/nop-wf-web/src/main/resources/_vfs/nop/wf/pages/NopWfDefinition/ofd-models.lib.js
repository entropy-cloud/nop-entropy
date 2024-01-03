//generate from /nop/wf/pages/NopWfDefinition/ofd-models.lib.xjs
System.register(['react', '@nop-chaos/nop-sdk'], (function () {
  'use strict';
  var createElement, NodeContext, registerFlowModel;
  return {
    setters: [module => {
      createElement = module.createElement;
    }, module => {
      NodeContext = module.NodeContext;
      registerFlowModel = module.registerFlowModel;
    }],
    execute: (function () {

      function NodeComponent_start(){
          const node = useContext(NodeContext);
          return createElement(Fragment,
        null,
        [
          createElement("div",
            {
              className:"start-node",
            },
            node.label
          ),
        ]
      );
      }
          
      function NodeComponent_end(){
          const node = useContext(NodeContext);
          return createElement(Fragment,
        null,
        [
          createElement("div",
            {
              className:"end-node",
            },
            node.label
          ),
        ]
      );
      }
          
      function NodeComponent_signNode(){
          const node = useContext(NodeContext);
          return createElement(Fragment,
        null,
        [
          createElement("div",
            {
              className:"other-node",
            },
            node.label
          ),
        ]
      );
      }
          
      function NodeComponent_conditionNode(){
          const node = useContext(NodeContext);
          return createElement(Fragment,
        null,
        [
          createElement("div",
            {
              className:"condition-node",
            },
            node.label
          ),
        ]
      );
      }
          
      function NodeComponent_branchNode(){
          const node = useContext(NodeContext);
          return createElement(Fragment,
        null,
        [
          createElement("div",
            {
              className:"other-node",
            },
            node.label
          ),
        ]
      );
      }
          
      function NodeComponent_commonNode(){
          const node = useContext(NodeContext);
          return createElement(Fragment,
        null,
        [
          createElement("div",
            {
              className:"other-node",
            },
            node.label
          ),
        ]
      );
      }
          
      function NodeComponent_ccNode(){
          const node = useContext(NodeContext);
          return createElement(Fragment,
        null,
        [
          createElement("div",
            {
              className:"other-node",
            },
            node.label
          ),
        ]
      );
      }
          

          const registerNodes = [
          
              {
                type: "start",
                name: "start",
                label: "起始步骤",
                displayComponent: NodeComponent_start,
                
              },
          
              {
                type: "end",
                name: "end",
                label: "结束步骤",
                displayComponent: NodeComponent_end,
                
              },
          
              {
                type: "common",
                name: "sign-node",
                label: "审批步骤",
                displayComponent: NodeComponent_signNode,
                
              },
          
              {
                type: "common",
                name: "condition-node",
                label: "条件节点",
                displayComponent: NodeComponent_conditionNode,
                
              },
          
              {
                type: "common",
                name: "branch-node",
                label: "分支节点",
                displayComponent: NodeComponent_branchNode,
                conditionNodeType: "condition-node"
              },
          
              {
                type: "common",
                name: "common-node",
                label: "普通步骤",
                displayComponent: NodeComponent_commonNode,
                
              },
          
              {
                type: "common",
                name: "cc-node",
                label: "传阅步骤",
                displayComponent: NodeComponent_ccNode,
                
              },
          
          ];

          registerFlowModel("oa-flow", registerNodes);

    })
  };
}));
