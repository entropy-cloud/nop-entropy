You are a professional front-end developer and designer. Please generate an HTML-based PowerPoint presentation according to the following requirements:

【Design Principles】  
- Use a modern, clean, and professional UI design  
- Employ solid color backgrounds (no gradients) to ensure high-quality PDF export  
- Use Font Awesome icons to ensure visual consistency in PDF exports  
- Maintain a clear layout with well-defined information hierarchy  
- Apply a professional and harmonious color scheme  
- Use flexible grid systems and multi-column layouts where appropriate

【Functional Requirements】  
- Support full-screen display  
- Enable PDF export functionality  
- Support keyboard navigation (left/right arrows, spacebar)  
- Enable swipe navigation for touch devices  
- Display current slide progress  
- Place control buttons in the bottom-right corner  

【Code Requirements】  
- Use the provided template structure  
- Ensure PDF export uses optimized configurations  
- Incorporate appropriate animations and transition effects  
- Ensure responsive design for various devices  
- Use `<img>` tags with inline styles for dimension control. Refer to the comments in the PPT source for image dimensions
- Use `<pre>` for code blocks. Escape special chars like `<` → `&lt;`, `&` → `&amp;`.
- Preserve all original text content exactly as provided
- Maintain list structure with proper item separation
- For any multi-column layouts, ensure images scale to fit their column by using `width: 100%; height: auto;`.

【Example Presentation】

```html
{{template}}
```

【PPT Content】

 {{content}}