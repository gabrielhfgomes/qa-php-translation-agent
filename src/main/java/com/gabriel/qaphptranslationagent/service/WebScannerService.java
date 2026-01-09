package com.gabriel.qaphptranslationagent.service;
import com.gabriel.qaphptranslationagent.model.PageElement;
import com.microsoft.playwright.*;
import com.microsoft.playwright.options.AriaRole;
import com.microsoft.playwright.options.LoadState;
import org.springframework.stereotype.Service;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class WebScannerService {

    // Lista de rotas já visitadas para evitar loops infinitos
    private final Set<String> visitedRoutes = new HashSet<>();

    public List<PageElement> scanLocalhost(String url) {
        try (Playwright playwright = Playwright.create()) {
            Browser browser = playwright.chromium().launch(
                    new BrowserType.LaunchOptions().setHeadless(false).setSlowMo(500)
            );
            Page page = browser.newPage();
            page.navigate(url);

            // 1. Troca o idioma para Português (conforme passos anteriores)
            page.locator("#current-lang-text").click();
            page.getByText("Português").first().click();
            page.waitForLoadState(LoadState.NETWORKIDLE);

            // 2. Clique no span "Entrar" para abrir o popup
            // Usamos filter para garantir que pegamos exatamente o span com esse texto
            page.locator("span").filter(new Locator.FilterOptions().setHasText("Entrar")).first().click();

            // 4. Preenchimento do formulário usando os nomes sugeridos pelo Playwright
            // Isso resolve a ambiguidade porque o formulário de login geralmente tem labels diferentes
            page.getByRole(AriaRole.TEXTBOX, new Page.GetByRoleOptions().setName("Seu E-mail")).fill("sepevo6147@jparksky.com");

            // Para a senha, se houver erro de duplicidade, use .first() ou um seletor de atributo
            page.locator("input[type='password']").first().fill("12345678");

            // 5. Clique no span "Entrar" de dentro do formulário/popup
            // Como existem dois spans "Entrar" (o do topo e o do login),
            // focamos no que está visível ou usamos o .last() se o do popup for o último a carregar
            page.locator("span").filter(new Locator.FilterOptions().setHasText("Entrar")).last().click();

            // 6. Aguarda o redirecionamento ou carregamento após login
            page.waitForLoadState(LoadState.NETWORKIDLE);

            // Executa um script que clica em todos os elementos que possuem indicadores de submenu
            page.evaluate("""
                () => {
                    const triggers = document.querySelectorAll('.submenu-indicator, .language-toggle, [data-bs-toggle="dropdown"]');
                    triggers.forEach(el => {
                        try { el.click(); } catch(e) {}
                    });
                }
            """);

            // Força a visibilidade via CSS especificamente para os itens de menu
            page.addStyleTag(new Page.AddStyleTagOptions().setContent(
                    ".menuitem, .dropdown-section, .nav-submenu { display: block !important; visibility: visible !important; }"
            ));

            // Pequena espera para o DOM processar o estilo
            page.waitForTimeout(500);

            // Aguarda as animações e possíveis carregamentos de rede
            page.waitForLoadState(LoadState.NETWORKIDLE);

            // Agora sim, com o usuário logado e em português, fazemos o scan
            List<PageElement> elements = extractElements(page);

            browser.close();
            return elements;
        } catch (Exception e) {
            e.printStackTrace();
            return Collections.emptyList();
        }
    }

    public Map<String, List<PageElement>> discoverAndScan(String baseUrl) {
        Map<String, List<PageElement>> fullSiteMap = new HashMap<>();
        Queue<String> queue = new LinkedList<>();
        queue.add(baseUrl);

        try (Playwright playwright = Playwright.create()) {
            Browser browser = playwright.chromium().launch(new BrowserType.LaunchOptions());
            Page page = browser.newPage();

            while (!queue.isEmpty()) {
                String currentUrl = queue.poll();
                if (visitedRoutes.contains(currentUrl) || !currentUrl.startsWith(baseUrl)) continue;

                visitedRoutes.add(currentUrl);
                page.navigate(currentUrl);
                page.waitForLoadState();

                // 1. Escaneia elementos da página atual
                List<PageElement> elements = extractElements(page);
                fullSiteMap.put(currentUrl, elements);

                // 2. Encontra novos links para a fila
                List<String> links = page.locator("a").all().stream()
                        .map(l -> l.getAttribute("href"))
                        .filter(href -> href != null && !href.startsWith("#") && !href.startsWith("javascript"))
                        .map(href -> href.startsWith("http") ? href : baseUrl + (href.startsWith("/") ? "" : "/") + href)
                        .collect(Collectors.toList());

                queue.addAll(links);
            }
            browser.close();
        }
        return fullSiteMap;
    }

    private List<PageElement> extractElements(Page page) {
        page.waitForTimeout(1000);
        List<PageElement> found = new ArrayList<>();

        // Focamos em elementos que costumam ter texto de interface
        Locator locators = page.locator("p, span, h1, h2, h3, h4, h5, h6, a, button, label, li, .text-tertiary");

        for (int i = 0; i < locators.count(); i++) {
            Locator item = locators.nth(i);
            String text = item.innerText().trim();

            // 1. Filtro de Relevância: Texto muito curto ou muito longo costuma ser ruído
            if (text.length() < 2 || text.length() > 300) continue;

            // 2. Filtro de Tradução: Se já tem data-translate, ignoramos (já está resolvido no código)
            String currentKey = item.getAttribute("data-translate");
            if (currentKey != null && !currentKey.isEmpty()) continue;

            // 3. Filtro de Visibilidade Real: Evita pegar textos de scripts ou tags ocultas
            if (!item.isVisible()) continue;

            String xpath = (String) item.evaluate("""
            el => {
                const getPath = (element) => {
                    if (element.id && element.id !== '') return `id("${element.id}")`;
                    if (element === document.body) return element.tagName;
                    let ix = 0;
                    let siblings = element.parentNode.childNodes;
                    for (let i = 0; i < siblings.length; i++) {
                        let sibling = siblings[i];
                        if (sibling === element) return getPath(element.parentNode) + '/' + element.tagName + '[' + (ix + 1) + ']';
                        if (sibling.nodeType === 1 && sibling.tagName === element.tagName) ix++;
                    }
                };
                return getPath(el);
            }
        """);

            found.add(new PageElement(text, "xpath=" + xpath, currentKey));
        }

        // Mantemos apenas a versão mais específica (ex: se tiver um span dentro de um li, pegamos o span)
        return found.stream()
                .collect(Collectors.toMap(
                        PageElement::text,
                        p -> p,
                        (existing, replacement) -> existing.selector().length() > replacement.selector().length() ? existing : replacement
                ))
                .values().stream().toList();
    }
}