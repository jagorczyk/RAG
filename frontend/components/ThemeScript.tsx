import Script from "next/script";

const themeInitScript = `(function(){try{document.documentElement.setAttribute('data-theme','light');document.documentElement.style.colorScheme='light';}catch(e){}})();`;

export function ThemeScript() {
  return (
    <Script id="theme-init" strategy="beforeInteractive">
      {themeInitScript}
    </Script>
  );
}
