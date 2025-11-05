h(Fragment,
  null,
  [
    h("div",
      null,
      [
        h("ul",
          null,
          [
            (items).map((item,index)=>{
              return h("li",
                null,
                [
                  item.isVisible ? h("span",
                    null,
                    item.name
                  ): null,
                ]
              )
            },
          ]
        ),
        h(ChildComponent,
          {
            default: scopeVariable => {
              return h("p",
                null,
                scopeVariable
              )
            }
          },
          null
        ),
        h("button",
          {
            className:btnClass,
            onClick:handleClick
          },
          "Click me"
        ),
      ]
    ),
  ]
)