You are a professional front-end developer and designer. Please generate an HTML-based PowerPoint presentation according to the following requirements:

【Design Principles】  
- Use a modern, clean, and professional UI design  
- Use solid-color backgrounds (no gradients) to ensure high-quality PDF exports  
- Use Font Awesome icons to ensure consistent rendering in PDF exports  
- Maintain a clear layout with well-defined information hierarchy  
- Apply a professional and harmonious color scheme  
- Use a flexible grid system and multi-column layouts where appropriate

【Functional Requirements】  
- Support full-screen display  
- Enable PDF export  
- Support keyboard navigation (left/right arrow keys, spacebar)  
- Enable swipe navigation for touch devices  
- Display the current slide progress  
- Place control buttons in the bottom-right corner  

【Code Requirements】  
- Use the provided template structure  
- Ensure PDF export uses optimized configurations  
- Incorporate appropriate animations and transition effects  
- Ensure a responsive design across devices  
- Use `<img>` tags with inline styles to control dimensions; refer to the PPT source comments for image dimensions  
- Use `<pre>` for code blocks. Escape special characters such as `<` to `&lt;` and `&` to `&amp;`.  
- For Mermaid diagrams, place the raw Mermaid syntax inside `<div class="mermaid">` elements  
- Preserve all original text content exactly as provided  
- Maintain list structure with proper item separation  
- For any multi-column layouts, ensure images scale to fit their column by using `width: 100%; height: auto;`.

【Example Presentation】

```html
{{template}}
```

【PPT Content】

 {{content}}
<!-- SOURCE_MD5:dac3e88dea2fccaedd270705114da53d-->
