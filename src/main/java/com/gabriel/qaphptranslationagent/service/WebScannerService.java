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
        // Dá um tempo extra para as animações dos menus que você clicou terminarem
        page.waitForTimeout(1000);

        List<PageElement> found = new ArrayList<>();

        // Expandimos os seletores para garantir que peguemos textos dentro de itens de menu específicos
        // Adicionamos 'p' e 'div' com critério de texto
        Locator locators = page.locator("p, span, h1, h2, h3, h4, h5, h6, b, strong, a, button, li, label, .text-tertiary");

        for (int i = 0; i < locators.count(); i++) {
            Locator item = locators.nth(i);
            String text = item.innerText().trim();

            // Verificamos se tem texto e se não é um elemento "pai" gigante que contém outros textos
            // (Isso evita pegar o header inteiro como uma única string)
            if (!text.isEmpty() && text.length() < 500) {

                // Verificamos se o elemento tem o atributo data-translate
                String currentKey = item.getAttribute("data-translate");

                // Pegamos o XPath para o Agente saber onde o texto mora
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
        }
        // Removemos duplicatas de texto que podem aparecer por causa de seletores sobrepostos (ex: span dentro de p)
        return found.stream()
                .collect(Collectors.toMap(PageElement::text, p -> p, (p1, p2) -> p1))
                .values().stream().toList();
    }
}