You are a professional front-end developer and designer. Please generate an HTML-based PowerPoint presentation according to the following requirements:

【Design Principles】  
- Use a modern, clean, and professional UI design  
- Employ solid color backgrounds (no gradients) to ensure high-quality PDF export  
- Use Font Awesome icons to ensure visual consistency in PDF exports  
- Maintain a clear layout with well-defined information hierarchy  
- Apply a professional and harmonious color scheme  

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


【Example Presentation】

```html
<!DOCTYPE html>
<html lang="en">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <title>Delta-Oriented Framework Presentation</title>
    <link rel="stylesheet" href="https://cdnjs.cloudflare.com/ajax/libs/font-awesome/6.4.0/css/all.min.css">
    <script src="https://cdnjs.cloudflare.com/ajax/libs/html2pdf.js/0.10.1/html2pdf.bundle.min.js"></script>
    <style>
        * {
            margin: 0;
            padding: 0;
            box-sizing: border-box;
            font-family: 'Inter', 'SF Pro Display', -apple-system, BlinkMacSystemFont, sans-serif;
        }
        
        body {
            background: #f5f7fa;
            color: #2d3748;
            min-height: 100vh;
            display: flex;
            justify-content: center;
            align-items: center;
            padding: 20px;
            overflow: hidden;
        }
        
        .presentation-container {
            width: 90%;
            max-width: 1200px;
            background: #ffffff;
            border-radius: 16px;
            box-shadow: 0 10px 30px rgba(0, 0, 0, 0.1);
            overflow: hidden;
            position: relative;
            height: 85vh;
            border: 1px solid #e2e8f0;
            display: flex;
            flex-direction: column;
        }
        
        .slides {
            flex: 1;
            overflow-y: auto;
            padding: 20px;
            scroll-snap-type: y mandatory;
        }
        
        .slide {
            min-height: calc(100% - 40px);
            padding: 30px;
            scroll-snap-align: start;
            display: none;
            animation: fadeIn 0.6s ease;
            background: white;
            border-radius: 12px;
            margin-bottom: 20px;
            overflow: hidden;
        }
        
        .slide.active {
            display: flex;
            flex-direction: column;
            justify-content: center;
        }
        
        .title-slide {
            text-align: center;
            display: flex;
            flex-direction: column;
            justify-content: center;
            align-items: center;
            background: #4c6ef5;
            color: white;
            border-radius: 16px;
            padding: 40px;
            height: 100%;
        }
        
        h1 {
            font-size: 2.8rem;
            margin-bottom: 24px;
            color: #2d3748;
            font-weight: 700;
            line-height: 1.2;
            letter-spacing: -0.5px;
        }
        
        h2 {
            font-size: 2rem;
            margin-bottom: 28px;
            color: #4a5568;
            font-weight: 600;
            padding-bottom: 12px;
            border-bottom: 2px solid #e2e8f0;
            line-height: 1.3;
        }
        
        h3 {
            font-size: 1.5rem;
            margin: 20px 0 16px;
            color: #4c6ef5;
            font-weight: 600;
        }
        
        .title-slide h1 {
            font-size: 3.2rem;
            color: white;
            margin-bottom: 16px;
        }
        
        .title-slide p {
            font-size: 1.8rem;
            color: rgba(255, 255, 255, 0.95);
            font-weight: 400;
        }
        
        ul {
            list-style-type: none;
            margin: 24px 0;
        }
        
        .icon-bullet {
            display: flex;
            align-items: flex-start;
            gap: 14px;
            margin-bottom: 18px;
            padding: 14px;
            border-radius: 10px;
            background: #f8fafc;
            transition: all 0.3s ease;
            white-space: normal;
            word-spacing: normal;
        }
        
        .icon-bullet:hover {
            background: #f1f5f9;
            transform: translateX(5px);
        }
        
        .icon-bullet i {
            color: #4c6ef5;
            font-size: 1.3rem;
            margin-top: 4px;
            flex-shrink: 0;
        }
        
        .controls {
            position: sticky;
            bottom: 0;
            background: white;
            padding: 15px 20px;
            display: flex;
            gap: 10px;
            z-index: 100;
            justify-content: flex-end;
            border-top: 1px solid #e2e8f0;
        }
        
        .btn {
            padding: 10px 16px;
            background: #4c6ef5;
            color: white;
            border: none;
            border-radius: 8px;
            cursor: pointer;
            font-weight: 600;
            transition: all 0.3s ease;
            box-shadow: 0 2px 8px rgba(76, 110, 245, 0.3);
            display: flex;
            align-items: center;
            gap: 8px;
            font-size: 0.85rem;
        }
        
        .btn:hover {
            transform: translateY(-2px);
            box-shadow: 0 4px 12px rgba(76, 110, 245, 0.4);
        }
        
        .btn:active {
            transform: translateY(0);
        }
        
        .fullscreen-btn {
            background: #7950f2;
        }
        
        .export-btn {
            background: #2fb344;
            box-shadow: 0 2px 8px rgba(47, 179, 68, 0.3);
        }
        
        .export-btn:hover {
            box-shadow: 0 4px 12px rgba(47, 179, 68, 0.4);
        }
        
        .progress-bar {
            position: absolute;
            bottom: 0;
            left: 0;
            height: 4px;
            background: #4c6ef5;
            width: 0%;
            transition: width 0.3s ease;
			z-index:101;
        }
        
        .slide-number {
            position: absolute;
            bottom: 20px;
            left: 20px;
            font-size: 1rem;
            color: #4c6ef5;
            font-weight: 600;
            background: #f8fafc;
            padding: 6px 12px;
            border-radius: 8px;
            box-shadow: 0 2px 8px rgba(0, 0, 0, 0.08);
			z-index:101;
        }
        
        .code-block {
            background: #f8fafc;
            color: #2d3748;
            padding: 20px;
            border-radius: 10px;
            margin: 20px 0;
            overflow-x: auto;
            font-family: 'Fira Code', 'Monaco', monospace;
            border: 1px solid #e2e8f0;
            font-size: 0.95rem;
            line-height: 1.5;
            white-space: pre-wrap;
            word-spacing: normal;
        }
        
        .img-placeholder {
            display: flex;
            justify-content: center;
            align-items: center;
        }
        
        .two-column {
            display: flex;
            gap: 30px;
            margin: 24px 0;
        }
        
        .col {
            flex: 1;
            background: #f8fafc;
            padding: 24px;
            border-radius: 12px;
            border: 1px solid #e2e8f0;
        }
        
        .formula {
            text-align: center;
            font-size: 1.5rem;
            font-family: 'Georgia', serif;
            margin: 20px 0;
            padding: 20px;
            background: #f8fafc;
            border-radius: 12px;
            color: #4a5568;
            border: 2px solid #e2e8f0;
            font-weight: 500;
        }
        
        .highlight {
            background: #f0fff4;
            padding: 24px;
            border-radius: 12px;
            margin: 20px 0;
            border-left: 5px solid #48bb78;
        }
        
        .key-point {
            color: #4c6ef5;
            font-weight: 700;
        }
        
        @keyframes fadeIn {
            from { opacity: 0; transform: translateY(15px); }
            to { opacity: 1; transform: translateY(0); }
        }
        
        /* PDF export specific style */
        @media print {
            body, html {
                width: 100%;
                height: auto;
                margin: 0;
                padding: 0;
                background: white !important;
            }
            
            .presentation-container {
                width: 100% !important;
                max-width: 100% !important;
                height: auto !important;
                box-shadow: none !important;
                background: white !important;
                border: none !important;
                border-radius: 0 !important;
            }
            
            .slides {
                height: auto !important;
                padding: 0 !important;
                overflow: visible !important;
            }
            
            .slide {
                display: block !important;
                break-inside: avoid;
                page-break-inside: avoid;
                height: auto !important;
                min-height: auto !important;
                margin: 0 0 20mm 0 !important;
                padding: 15mm !important;
                border: 1px solid #ddd !important;
                border-radius: 5px !important;
                box-shadow: none !important;
                position: relative;
            }
            
            .btn, .controls, .progress-bar, .slide-number {
                display: none !important;
            }
            
            .title-slide {
                page-break-after: always;
                break-after: page;
            }
        }

        .pdf-page {
            width: 100%;
            height: auto;
            background: white;
            color: black;
            box-shadow: none;
        }

        .pdf-slide {
            width: 100%;
            margin-bottom: 20px;
            break-inside: avoid;
            page-break-inside: avoid;
            border: 1px solid #eeeeee;
            border-radius: 8px;
            padding: 30px;
            background: white;
            box-shadow: none;
            position: relative;
            display: block;
            height: auto;
        }
        
        @media (max-width: 900px) {
            h1 {
                font-size: 2.2rem;
            }
            
            h2 {
                font-size: 1.8rem;
            }
            
            .title-slide h1 {
                font-size: 2.4rem;
            }
            
            .two-column {
                flex-direction: column;
            }
            
            .slide {
                padding: 25px 20px;
            }
            
            .slides {
                padding: 15px;
            }
            
            .controls {
                padding: 10px 15px;
            }
            
            .btn {
                padding: 8px 12px;
                font-size: 0.8rem;
            }
			
			
        }
    </style>
</head>
<body>
    <div class="presentation-container">
        <div class="slides">
            <!-- Slide 1 -->
            <div class="slide active">
                <div class="title-slide">
                    <h1>Extensibility based on Delta-Oriented Framework</h1>
                    <p>2025/09</p>
                </div>
            </div>
            
            <!-- Slide 2 -->
            <div class="slide">
                <h2>The Customization Trap</h2>
                <div class="icon-bullet">
                    <i class="fas fa-copy"></i>
                    <div><span class="key-point">Copy & Change:</span> The seemingly easy start - duplicate the core product for each custom need</div>
                </div>
                <div class="icon-bullet">
                    <i class="fas fa-code-branch"></i>
                    <div><span class="key-point">Diverging Paths:</span> The inevitable conflict - core evolves while custom versions stagnate</div>
                </div>
                <div class="icon-bullet">
                    <i class="fas fa-exclamation-triangle"></i>
                    <div><span class="key-point">Technical Debt:</span> The end result - core misses innovations, custom versions become unmaintainable legacy</div>
                </div>
            </div>
            
            <!-- Slide 3 -->
            <div class="slide">
                <h2>Software Product Lines: From Ad Hoc Customization to System-Level Reuse</h2>
                <div class="img-placeholder"><img src=“reuse-history.png" style="width:500px;height:auto"/> </div>
                <div class="icon-bullet">
                    <i class="fas fa-stethoscope"></i>
                    <div><span class="key-point">Diagnosis:</span> "The Customization Trap" caused by unmanaged variation</div>
                </div>
                <div class="icon-bullet">
                    <i class="fas fa-lightbulb"></i>
                    <div><span class="key-point">The Insight:</span> Reuse the entire system, not just parts - engineer the whole product family</div>
                </div>
                <div class="icon-bullet">
                    <i class="fas fa-sitemap"></i>
                    <div><span class="key-point">Two-Lifecycle Model:</span> Domain Engineering (for reuse) and Application Engineering (with reuse)</div>
                </div>
            </div>
        </div>
        
        <div class="progress-bar"></div>
        <div class="slide-number">Slide 1 of 5</div>
        
        <div class="controls">
            <button class="btn prev-btn"><i class="fas fa-arrow-left"></i> Prev</button>
            <button class="btn next-btn">Next <i class="fas fa-arrow-right"></i></button>
            <button class="btn fullscreen-btn"><i class="fas fa-expand"></i> Full</button>
            <button class="btn export-btn" id="export-pdf"><i class="fas fa-file-pdf"></i> PDF</button>
        </div>
    </div>

    <script>
        document.addEventListener('DOMContentLoaded', function() {
            const slides = document.querySelectorAll('.slide');
            const prevBtn = document.querySelector('.prev-btn');
            const nextBtn = document.querySelector('.next-btn');
            const fullscreenBtn = document.querySelector('.fullscreen-btn');
            const exportPdfBtn = document.getElementById('export-pdf');
            const slideNumber = document.querySelector('.slide-number');
            const progressBar = document.querySelector('.progress-bar');
            const container = document.querySelector('.presentation-container');
            
            let currentSlide = 0;
            
            // Initialize
            updateSlideNumber();
            updateProgressBar();
            
            // Next button
            nextBtn.addEventListener('click', function() {
                if (currentSlide < slides.length - 1) {
                    slides[currentSlide].classList.remove('active');
                    currentSlide++;
                    slides[currentSlide].classList.add('active');
                    updateSlideNumber();
                    updateProgressBar();
                }
            });
            
            // Previous button
            prevBtn.addEventListener('click', function() {
                if (currentSlide > 0) {
                    slides[currentSlide].classList.remove('active');
                    currentSlide--;
                    slides[currentSlide].classList.add('active');
                    updateSlideNumber();
                    updateProgressBar();
                }
            });
            
            // Fullscreen button
            fullscreenBtn.addEventListener('click', function() {
                if (!document.fullscreenElement) {
                    if (container.requestFullscreen) {
                        container.requestFullscreen();
                    } else if (container.webkitRequestFullscreen) {
                        container.webkitRequestFullscreen();
                    } else if (container.msRequestFullscreen) {
                        container.msRequestFullscreen();
                    }
                } else {
                    if (document.exitFullscreen) {
                        document.exitFullscreen();
                    } else if (document.webkitExitFullscreen) {
                        document.webkitExitFullscreen();
                    } else if (document.msExitFullscreen) {
                        document.msExitFullscreen();
                    }
                }
            });
            
            // Export to PDF - All slides
            document.getElementById('export-pdf').addEventListener('click', function() {
                //create pdf container
                const pdfContainer = document.createElement('div');
                pdfContainer.className = 'pdf-page';
                
                // clone all slides
                const slides = document.querySelectorAll('.slide');
                slides.forEach((slide, index) => {
                    const clone = slide.cloneNode(true);
                    clone.className = 'pdf-slide';
                    clone.style.display = 'block';
                    clone.style.opacity = '1';
                    clone.style.transform = 'none';
                    clone.style.visibility = 'visible';
                    
                    // Remove styles that may affect PDF rendering
                    clone.style.boxShadow = 'none';
                    clone.style.background = 'white';
                    clone.style.color = 'black';
                    
                    // Ensure proper text spacing
                    const allTextElements = clone.querySelectorAll('*');
                    allTextElements.forEach(el => {
                        el.style.wordSpacing = 'normal';
                        el.style.letterSpacing = 'normal';
                        el.style.whiteSpace = 'normal';
                    });
                    
                    pdfContainer.appendChild(clone);
                });
                
                // Add to document
                document.body.appendChild(pdfContainer);
                
                // Set PDF options
                const opt = {
                    margin: 10,
                    filename: 'delta-oriented-framework.pdf',
                    image: { 
                        type: 'jpeg', 
                        quality: 0.98 
                    },
                    html2canvas: { 
                        scale: 2,
                        useCORS: true,
						allowTaint: true,
                        logging: true,
                        backgroundColor: '#FFFFFF',
                        letterRendering: true,
                    },
                    jsPDF: { 
                        unit: 'mm', 
                        format: 'a4', 
                        orientation: 'portrait' 
                    },
                    pagebreak: { mode: ['avoid-all', 'css', 'legacy'] }
                };
                
                // Generate PDF
                console.log('PDF container created with', pdfContainer.children.length, 'slides');
                
                html2pdf().set(opt).from(pdfContainer).save().then(() => {
                    // remove temporary container
                    document.body.removeChild(pdfContainer);
                    console.log('PDF generated successfully');
                }).catch(error => {
                    console.error('PDF generation error:', error);
                    document.body.removeChild(pdfContainer);
                });
            });
            
            // Keyboard navigation
            document.addEventListener('keydown', function(e) {
                if (e.key === 'ArrowRight' || e.key === ' ' || e.key === 'PageDown') {
                    nextBtn.click();
                } else if (e.key === 'ArrowLeft' || e.key === 'PageUp') {
                    prevBtn.click();
                } else if (e.key === 'f') {
                    fullscreenBtn.click();
                } else if (e.key === 'Home') {
                    goToSlide(0);
                } else if (e.key === 'End') {
                    goToSlide(slides.length - 1);
                }
            });
            
            // Update slide number
            function updateSlideNumber() {
                slideNumber.textContent = `Slide ${currentSlide + 1} of ${slides.length}`;
            }
            
            // Update progress bar
            function updateProgressBar() {
                const progress = ((currentSlide + 1) / slides.length) * 100;
                progressBar.style.width = `${progress}%`;
            }
            
            // Go to specific slide
            function goToSlide(n) {
                slides[currentSlide].classList.remove('active');
                currentSlide = n;
                slides[currentSlide].classList.add('active');
                updateSlideNumber();
                updateProgressBar();
            }
            
            // Swipe support for touch devices
            let touchStartX = 0;
            let touchEndX = 0;
            
            document.querySelector('.slides').addEventListener('touchstart', function(e) {
                touchStartX = e.changedTouches[0].screenX;
            });
            
            document.querySelector('.slides').addEventListener('touchend', function(e) {
                touchEndX = e.changedTouches[0].screenX;
                handleSwipe();
            });
            
            function handleSwipe() {
                const minSwipeDistance = 50;
                
                if (touchEndX < touchStartX && touchStartX - touchEndX > minSwipeDistance) {
                    nextBtn.click();
                } 
                
                if (touchEndX > touchStartX && touchEndX - touchStartX > minSwipeDistance) {
                    prevBtn.click();
                }
            }
        });
    </script>
</body>
</html>
```

【PPT Document】

 {{content}}