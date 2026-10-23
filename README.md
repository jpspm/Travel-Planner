# Travel Planner

App de planejamento de viagens: **um lugar só para a viagem inteira**. Roteiro por dia com fuso horário, reservas com QR code e PDF, mapa, fotos dos lugares, orçamento, checklist e o histórico depois que a viagem acaba.

- **Local-first e offline:** sem servidor, sem login e sem conta. Os dados ficam no aparelho.
- **Custo zero:** só APIs gratuitas, sem chave (OpenStreetMap, Wikimedia, OpenFreeMap).
- **Kotlin Multiplatform + Compose Multiplatform:** o Android é a plataforma principal. O iOS está preparado na arquitetura, mas não foi compilado nem testado (não há Mac).

As decisões de arquitetura e os requisitos estão nos ADRs, na pasta acima deste projeto:

- [ADR-001 — Arquitetura e requisitos do MVP](../../ADR-001-arquitetura-e-requisitos.md)
- [ADR-002 — Próximos passos](../../ADR-002-proximos-passos.md)

---

## Funcionalidades

| Área | O que tem |
|---|---|
| **Viagens** | Criar, editar, duplicar e excluir; foto de capa da galeria ou de um lugar do roteiro; separação em em andamento, próximas e passadas |
| **Hoje** | Cartão na tela inicial com o próximo compromisso e os ingressos e passagens do dia (QR code em 2 toques) |
| **Roteiro** | Dias com atividades e reservas, horário opcional, **fuso IANA em cada item** (`14:00 (Roma, UTC+2)`), ordem pelo instante real entre fusos, mover entre dias |
| **Mapa** | Mapa interativo no topo do roteiro (dia ou viagem inteira); **imagem estática salva** para ver sem internet |
| **Planejamento** | Alertas de conflito (horários sobrepostos, atividade durante voo, noite sem hospedagem, lugar fechado), distância e tempo a pé entre itens, sugestão de ordem do dia |
| **Lugares** | Busca com mapa (sugestões ao digitar e busca completa em português), foto da Wikimedia com créditos, horário de funcionamento, fuso sugerido pelas coordenadas, abrir no app de mapas |
| **Ideias** | Lista do que fazer sem dia definido, com as mesmas categorias do roteiro (atividade ou tipo de reserva), agrupada por cidade. Inclui **atrações por perto**, da Wikipédia. Cada ideia vai para o roteiro com um toque |
| **Reservas** | Voo, hospedagem, ingresso, trem/ônibus, carro, restaurante e outro, cada uma com seus campos; anexos (imagem, print, PDF, foto) abertos em tela cheia com brilho máximo |
| **Cidades** | Detectadas sozinhas pelos lugares buscados (raio de 40 km) ou cadastradas à mão; tela por cidade com mapa e roteiro filtrado |
| **Gastos** | Orçamento total e por categoria, gastos avulsos, valor das reservas somado automaticamente, planejado × gasto |
| **Checklist** | Itens pré-viagem, com sugestões para começar |
| **Resumo** | Viagens passadas abrem num resumo com números, cidades, fotos e gasto total |
| **Compartilhar** | Roteiro em **HTML autocontido** (um arquivo, sem anexos, códigos nem valores), enviado pelo menu do sistema |
| **Backup** | Exportar e importar `.zip` (todas as viagens ou uma), mesclar ou substituir, com lembrete quando faz tempo que não há backup |
| **Lembretes** *(Android)* | Notificações antes de voos e trens, outras reservas e atividades, com antecedência ajustável |
| **Compartilhar para o app** *(Android)* | Receber PDF ou print de outro app ("Compartilhar → Travel Planner") e anexar a uma reserva |
| **Widget "Hoje"** *(Android)* | Viagem do dia, próximo compromisso e ingressos de hoje na tela inicial |

A interface é **só em pt-BR** e a moeda é **BRL**. Os textos ficam centralizados nos recursos do Compose, para facilitar outros idiomas no futuro.

---

## Stack

| Camada | Escolha |
|---|---|
| Linguagem e UI | Kotlin 2.4, Compose Multiplatform 1.12, Material 3 |
| Navegação | Navigation Compose (rotas tipadas), transições horizontais |
| Persistência | Room KMP com SQLite empacotado (`sqlite-bundled`), migrações automáticas versionadas |
| Datas e fusos | `kotlinx-datetime` (`LocalDateTime` + fuso IANA) |
| Rede | Ktor Client (OkHttp no Android, Darwin no iOS) |
| Imagens | Coil 3 (com Ktor e integração com o FileKit) |
| Mapa | MapLibre Compose + estilo vetorial do OpenFreeMap |
| Arquivos | FileKit (seletores, câmera, compartilhar) |
| Injeção de dependência | Koin |
| Serialização | `kotlinx-serialization` (JSON do backup) |

**APIs gratuitas usadas (sem chave):**

| Serviço | Para quê |
|---|---|
| **Photon** (komoot) | Sugestões de lugar enquanto digita |
| **Nominatim** (OSM) | Busca completa com nomes em português, Wikidata e horário de funcionamento (máximo de 1 requisição/s, só por ação do usuário) |
| **Wikidata, Wikimedia Commons, Wikipédia** | Foto do lugar com créditos e atrações por perto |
| **OpenFreeMap** | Mapa vetorial (atribuição sempre visível) |

Todas as requisições se identificam com o `User-Agent` do app.

---

## Estrutura do código

O código fica quase todo em `shared/src/commonMain` (pacote `com.jpspm.travelplanner`):

```
core/          tempo e fusos, dinheiro (BRL), geografia, IDs
domain/        regras puras, sem dependência de plataforma
  model/         Trip, Activity, Reservation, Place…
  agenda/        monta o roteiro dia a dia
  planning/      conflitos, tempo a pé, ordem sugerida
  places/        horário de funcionamento (opening_hours)
  city/          agrupamento por cidade
  budget/        planejado × gasto
  reminders/     cálculo dos lembretes
  export/        HTML do roteiro
  map/           o que vai em cada imagem estática do mapa
  repository/    interfaces dos repositórios
data/          implementações: Room, arquivos, APIs, backup, exportação, sincronização em segundo plano
ui/            telas Compose, ViewModels, navegação e tema
platform/      expect/actual e interfaces de plataforma
di/            módulos do Koin
```

- `shared/src/androidMain`: banco, PDF, brilho de tela, ZIP, notificações, widget, compartilhamento de entrada.
- `shared/src/iosMain`: os `actual` equivalentes, ainda não compilados.
- `androidApp/`: Activity, Application, manifesto e ícone.
- `iosApp/`: ponto de entrada do iOS (Xcode).
- `shared/schemas/`: schema exportado de cada versão do banco (hoje até a v7).
- `design/`: ícone (`icon.svg`) e o script que gera o ícone adaptativo do Android.

---

## Como rodar

**Requisitos:** Android Studio (ou JDK 21+ e Android SDK com a plataforma 37). O `local.properties` precisa apontar para o SDK (`sdk.dir=...`); o Android Studio cria esse arquivo sozinho.

| Tarefa | Comando |
|---|---|
| Gerar o APK de debug | `./gradlew :androidApp:assembleDebug` |
| Instalar no celular ou emulador | `./gradlew :androidApp:installDebug` |
| Testes (regras e formatos, na JVM) | `./gradlew :shared:testAndroidHostTest` |
| Testes no simulador iOS (só com Mac) | `./gradlew :shared:iosSimulatorArm64Test` |
| Regenerar o ícone do Android | `python design/make_android_icon.py` |

No Windows, use `gradlew.bat`. **iOS:** abra `iosApp/` no Xcode, num Mac.

**Se um recurso do Android "sumir" no build:** o cache do Gradle está ligado (`org.gradle.caching=true`). Se um recurso renomeado ou movido não for encontrado, rode `./gradlew :androidApp:mergeDebugResources --rerun-tasks` e gere o APK de novo.

---

## Testes

Os testes ficam em `shared/src/commonTest` e cobrem as regras de domínio (RNF-20 e RNF-29):

- fusos, horário de verão e formatação;
- dinheiro em BRL;
- montagem do roteiro e ordem entre fusos;
- conflitos, tempo a pé e ordem sugerida;
- interpretador de `opening_hours`;
- agrupamento por cidade;
- orçamento;
- lembretes;
- busca e fotos (com respostas simuladas, sem rede);
- formato do backup e proteção contra arquivos maliciosos ("zip slip");
- HTML exportado (garante que não vazam códigos, valores nem anexos);
- projeção do mapa.

O ZIP é testado em `androidHostTest`.

---

## Dados e privacidade

- Tudo fica no armazenamento privado do app: banco SQLite e pasta de arquivos com anexos, fotos e mapas. Os caminhos são guardados de forma relativa.
- Nada é enviado a servidor próprio. A internet só é usada para buscar lugares, fotos, atrações e o mapa.
- **Backup:** `.zip` com `manifest.json` (`schemaVersion`), `data.json` e `files/`. Versões novas do app importam backups antigos.
- **HTML compartilhado:** nunca inclui anexos, códigos de reserva, valores, notas nem detalhes das reservas.

---

## Plataformas

| | Android | iOS |
|---|---|---|
| App principal | ✅ minSdk 26, testado em aparelho | ⚠️ código preparado, nunca compilado |
| Backup em ZIP | ✅ | ❌ falta implementar o ZIP |
| Lembretes, widget, receber arquivos | ✅ | ❌ fora do escopo atual (ADR-002, RNF-28) |
| Detecção de internet | ✅ | ⚠️ sempre "conectado" por enquanto |
