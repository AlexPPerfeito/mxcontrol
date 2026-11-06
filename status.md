# STATUS DO PROJETO: MX CONTROL (Yamaha MX49 / MX61 / MX88)

> **AVISO PARA O AGENTE ANTIGRAVITY:**
> Este arquivo é a **fonte única da verdade (Single Source of Truth)** compartilhada entre os computadores de desenvolvimento.
> - **Sempre leia este arquivo** ao iniciar uma nova sessão ou trocar de máquina.
> - **Sempre atualize este arquivo** ao implementar novas funcionalidades, corrigir bugs, alterar versões ou planejar novas etapas.
> - **Nunca altere o código em produção** sem instrução expressa se houver uma versão em análise na Google Play Store.

---

## 1. Identificação Geral do Projeto

* **Nome do Aplicativo**: MX Control
* **Pacote Android**: `com.yamahamx.editor`
* **Público-Alvo**: Músicos tecladistas (shows, igrejas, estúdio) utilizando sintetizadores Yamaha MX49, MX61 e MX88.
* **Stack Tecnológico**:
  * Linguagem: Kotlin (JVM 17)
  * Framework UI: Jetpack Compose + Material 3 (Design System Synth escuro de alto contraste)
  * Arquitetura: MVVM + Coroutines + Kotlin StateFlow / SharedFlow
  * Persistência: Room Database + SharedPreferences
  * Comunicação MIDI: Arquitetura Híbrida (Android Native MIDI API para Class-Compliant / MX V2 + Driver USB Host Direto em Espaço de Usuário via UsbManager / Bulk Transfer para Yamaha MX V1 - Vendor ID 0x0499)

---

## 2. Versionamento e Publicação (Google Play)

| Parâmetro | Valor Atual | Situação / Notas |
| :--- | :--- | :--- |
| **versionCode** | `44` | Atual no `app/build.gradle.kts` |
| **versionName** | `"1.3.7"` | Build 44 - Suporte completo ao idioma Espanhol (i18n / `values-es`), promoção Black Friday e alinhamento Pro |
| **Versão Anterior (Play Store)** | `43` / `"1.3.6"` | Substituída por Build 44 (v1.3.7) |
| **Keystore de Release** | `app/mxcontrol-release.jks` | Alias: `mxcontrol` / Senha: `mxcontrol2026` |

---

## 3. Ambiente de Desenvolvimento e Comandos de Build

Para garantir compilação idêntica entre máquinas com o JDK 17:

### Configuração do Ambiente (PowerShell):
```powershell
$env:JAVA_HOME = "C:\Program Files\Eclipse Adoptium\jdk-17.0.20.101-hotspot"
```

### Comandos de Compilação:
* **Gerar APK de Release (para teste em celular via sideload)**:
  ```powershell
  $env:JAVA_HOME = "C:\Program Files\Eclipse Adoptium\jdk-17.0.20.101-hotspot"; .\gradlew.bat assembleRelease
  ```
  *Saída:* `app/build/outputs/apk/release/app-release.apk`

* **Gerar Bundle AAB Assinado (para upload na Google Play Store)**:
  ```powershell
  $env:JAVA_HOME = "C:\Program Files\Eclipse Adoptium\jdk-17.0.20.101-hotspot"; .\gradlew.bat bundleRelease
  ```
  *Saída:* `app/build/outputs/bundle/release/app-release.aab`

* **Executar Testes Unitários**:
  ```powershell
  $env:JAVA_HOME = "C:\Program Files\Eclipse Adoptium\jdk-17.0.20.101-hotspot"; .\gradlew.bat test
  ```

---

## 4. Funcionalidades Já Implementadas (Changelog Recente)

### v1.1.4 (Build 16 a 19), v1.1.5 (Build 20) & v1.1.6 (Build 21)
1. **Renomeação de Categorias/Tags da Setlist via Long-Press**:
   * Toque longo (> 2s) nos botões de filtro de categoria (`GERAL`, `ROCK`, `IGREJA`, `POP`, `JAZZ`, `ENSAIO`) abre diálogo para renomear.
   * O botão `TODAS` permanece fixo como filtro global de segurança.
   * Nomes personalizados são salvos localmente e persistem entre sessões.
2. **Controle de Modulação / Rotary Speaker (MIDI CC 1)**:
   * **Tela "Parte" (PartDetailScreen)**: Card dedicado com slider contínuo (0..127), visor numérico e botão toggle rápido `SLOW / FAST` (0 / 127).
   * **Tela "Mixer" (MixerScreen)**: Mini fader horizontal em cada canal (`PartStripItem`) com display digital estilo LED e duplo toque para zerar instantaneamente.
3. **MidiThrottler (Proteção do Buffer USB-MIDI)**:
   * Intervalo de 20ms entre mensagens por parâmetro, evitando travamento ou sobrecarga do buffer do sintetizador.
4. **Panic / Safety Flush**:
   * Corte imediato de notas presas e liberação de pedal sustain (CC 64, CC 123, CC 120, CC 121) em todos os 16 canais.
5. **Roteamento de Layers e Split Points**:
   * Gerenciamento de camada (Dual/Layer) desacoplando o Part 1-2 Link do hardware e roteando canais de recepção com segurança.
6. **Controle On/Bypass de Modulação por Parte via SysEx Receive Switch (Opção B)**:
   * Permite desativar a modulação individualmente em cada uma das 16 partes (essencial para sons em layer, como Piano e Pad).
   * Atua diretamente na chave de hardware do sintetizador via SysEx (`0x37 [part] 0x38 [0=Off, 1=On]`).
   * Ao desativar (Bypass): bloqueia a roda física de modulação do teclado e mensagens de CC 1 do app, enviando imediatamente `CC 1 = 0` para extinguir modulações ativas residuais.
   * **Tela "Parte" (PartDetailScreen)**: Badge `RCV: ON / BYPASS`, slider atenuado e legenda de status.
   * **Tela "Mixer" (PartStripItem)**:
     * Visor digital LED de status com indicador duplo:
       - **Ativo**: Ponto LED ciano + `RCV` + `MOD <valor>` (em âmbar/branco com borda ciano sutil).
       - **Bypass**: Ponto LED vermelho + `BYPASS` (em vermelho negrito com fundo e borda avermelhados).
     * **Comportamento de Toque Síncrono**: Toque simples no visor LED alterna instantaneamente entre RCV ON e BYPASS (disparando `togglePartModulation`), mantendo sincronia total com a tela de Parte.
     * Toque duplo ou toque longo no visor ativo zera a modulação (`0`). Toque no slider desabilitado reativa a modulação.
   * Testes de unidade adicionados em `YamahaSysExEncoderTest.kt` validando os pacotes SysEx para as partes 1 e 2.
7. **Ajuste Conceitual no Header (Contador de Notas Ativas)**:
   * Atualizado o rótulo do badge no TopBar/Header de `POLY` para `NOTAS` (ex: `NOTAS $activeNotes/128`).
   * Como o Yamaha MX não transmite telemetria SysEx em tempo real do consumo dos elementos AWM2 internos do DSP, o app rastreia as notas ativas/sustentadas via MIDI, tornando o rótulo `NOTAS` conceitualmente exato e claro para o usuário.
8. **Orientação Adaptativa do Fader de Volume no Mixer (PartStripItem)**:
   * **Modo Retrato (Portrait)**: Preserva o fader vertical (`VerticalFader` com 115dp de altura) e visor numérico clássico.
   * **Modo Paisagem (Landscape)**: Renderiza o fader de volume como um slider **horizontal** (`Slider` com `fillMaxWidth()`), com label `VOL` e visor numérico (0..127). Aproveita toda a largura da faixa do canal (96dp) proporcionando excelente curso de toque e ergonomia, eliminando a compressão vertical em telas de palco/paisagem.
9. **Banco de Vozes USER (MSB 63 / LSB 8) com Nomes Personalizados**:
   * **Persistência Local (SQLite)**: Criada a entidade `UserVoiceEntity`, `UserVoiceDatabaseHelper` e `UserVoiceRepository` gerenciando os 128 slots de usuário (US001 a US128, MSB 63, LSB 8, Program Change 0..127).
   * **Seletor de Timbres (VoiceSelectorDialog)**: Chip dedicado `USER (128)` com listagem dos 128 slots e modal de renomeação rápida (via ícone de lápis ou toque longo) persistindo nomes de patches e packs locais.
   * **Transmissão MIDI Yamaha MX**: Disparo rigoroso de SysEx (Bank MSB 63, Bank LSB 8, Program Number) + mensagens de canal CC 0 = 63, CC 32 = 8 e Program Change.
   * **Identificação Visual**: Exibição do badge `[USR]` e elementos da categoria USER em azul padrão Synth (`YamahaCyan`), harmonizando perfeitamente com os demais bancos e categorias no seletor de timbres (`VoiceSelectorDialog`) e na faixa do Mixer (`PartStripItem`).
   * **Testes Unitários**: Testes adicionados em `UserVoiceEntityTest.kt` e `VoiceRepositoryTest.kt` com 100% de sucesso na suíte.
10. **Build 20 (v1.1.5) - Publicação na Google Play**:
    * Incremento para `versionCode = 20` e `versionName = "1.1.5"`.
    * Empacotamento assinado em Android App Bundle (.aab) para distribuição via teste fechado na Google Play Console.
11. **Refinamentos de UI/UX (Botão SOLO e Abertura Contextual do Seletor de Timbres)**:
    * **Botão SOLO (`PartDetailScreen.kt`)**: Adicionados `maxLines = 1`, `softWrap = false`, `defaultMinSize(minWidth = 46.dp)` e alinhamento centralizado com padding otimizado, eliminando a quebra de linha indesejada em telas estreitas e garantindo dimensões e harmonia simétrica com o botão MUTE.
    * **Abertura Contextual no Seletor de Timbres (`VoiceSelectorDialog.kt`)**: O diálogo agora detecta o timbre ativo atual (`currentVoice`) e abre diretamente filtrado na sua categoria de origem (ex: órgãos abrem na aba `ORG`, timbres de usuário na aba `USER`, pianos acústicos em `AP`, etc.).
    * **Auto-Scroll Inteligente**: O seletor rola automaticamente tanto a barra de categorias (`LazyRow`) até o chip correspondente quanto a lista de vozes (`LazyColumn`) até o timbre atual selecionado, mantendo-o em destaque ao abrir o modal.
12. **Estratégia Inteligente de Restauração de Backup/JSON (Merge vs Substituir Tudo)**:
    * **Validação Prévia de Dados**: Ao clicar em "Importar Performances" no `SetlistImportExportDialog`, o app valida e analisa previamente o JSON através de `PerformanceStorageRepository.parsePerformancesJson()`. Se o JSON for inválido ou vazio, o usuário é notificado imediatamente sem corromper a base.
    * **Detecção do Estado da Base**:
      * **Cenário A (Banco Vazio / 0 performances)**: Importação direta com novo UUID gerado para cada preset e notificação `"X performances restauradas com sucesso!"`.
      * **Cenário B (Banco com Dados Existentes / count > 0)**: Exibição de diálogo modal de confirmação estilizado:
        - **Mesclar / Somar** (botão ciano `YamahaCyan`): Adiciona as novas performances preservando integralmente as atuais, com geração de novo UUID único para evitar colisões de identificador e notificação `"X performances mescladas com sucesso! Total: Y"`.
        - **Substituir Tudo** (botão vermelho de advertência): Substitui todas as performances existentes pelas do backup e notifica `"Setlist substituído com sucesso! X performances restauradas."`.
        - **Cancelar** (botão neutro): Fecha o diálogo sem alterar nenhum registro.
    * **Testes Automatizados**: Suíte unitária implementada em `PerformanceStorageRepositoryTest.kt` cobrindo parsing de JSON, rejeição de entradas inválidas, estratégias de mesclagem e substituição, e garantia de unicidade de UUIDs, com 100% de aprovação no `./gradlew test`.
13. **Build 21 (v1.1.6) - Publicação na Google Play**:
    * Incremento para `versionCode = 21` e `versionName = "1.1.6"` para contornar a rejeição de versão duplicada (código 20 já utilizado) na Google Play Console.
    * Pacote assinado de produção gerado em `.aab` integrando todas as funcionalidades consolidadas de Backup/JSON (Merge vs Substituir), refinamentos do botão SOLO, seletor de timbres contextual e suporte ao Banco USER.
14. **Build 22 (v1.1.7 / Hotfix) - Correção Crítica no Setlist, Sincronização de Estado e Preservação de Parâmetros de Parte**:
    * **Prevenção de Duplicidade ao Salvar**:
      - `PerformanceViewModel.saveCurrentPerformance` agora suporta o parâmetro `saveAsNew: Boolean`.
      - Quando uma performance já está carregada (`loadedPerformanceId != null`), o fluxo padrão preserva o identificador UUID existente, realizando a atualização in-place no banco local sem duplicar presets.
      - O diálogo modal `SavePerformanceDialog` detecta performances carregadas ativas e exibe ações contextuais explícitas: botão **"Atualizar"** (`saveAsNew = false`) e botão secundário **"Salvar como Novo"** (`saveAsNew = true`), além de badge indicando o nome do preset em edição.
    * **Preservação de Parâmetros de Sintetizador ao Trocar Foco de Parte**:
      - Em `switchLiveFocusedPart` e `togglePartLayerSelection`, eliminada a emissão de SysEx de Voice Change (`createVoiceChangeMessages`). Na arquitetura do sintetizador físico Yamaha MX, mensagens de Voice Change forçam o DSP a resetar cutoff, ressonância, pan, volume e efeitos da parte para os valores de fábrica do preset.
      - Removida a sobreposição forçada de volume (`effectiveVol = 100`) para partes no canal 0. O volume configurado pelo usuário é preservado estritamente em memória e hardware.
      - Os 16 estados de parte (`MultiPartState`) permanecem 100% imutáveis durante a comutação de partes ao vivo, alterando apenas os canais de recepção MIDI (`receiveChannel`) necessários.
    * **Consistência Total de Timbres e Recomposição ao Recarregar**:
      - Implementado método `recarregarPerformance(performanceId)` que busca e recarrega a performance no banco, retransmitindo a carga completa de parâmetros e garantindo consistência em todas as abas.
      - Na reconstrução de estado (`loadPerformance`), todas as 16 partes são estritamente indexadas de 0 a 15 (`(0..15).map`), eliminando qualquer desalinhamento entre `selectedPartIndex` e a lista de partes, forçando recomposição atômica sincronizada entre as abas "Parte" e "Mixer".
      - Adicionados testes de unidade em `SavedPerformanceTest.kt` validando preservação de IDs e persistência imutável dos parâmetros de parte.
15. **Build 23 (v1.1.8-preview) - Modo de Pré-escuta (Audition/Preview) com Navegação +/- no Seletor de Timbres:**
    * **Pré-escuta Contínua (Audition Mode)**:
      - Ao tocar em qualquer linha de timbre no `VoiceSelectorDialog`, a voz é despachada imediatamente via MIDI (Bank Select MSB/LSB + Program Change) para o teclado físico tocar a nova voz na Parte selecionada, sem fechar o diálogo.
      - A alteração permanece temporária em memória, sem persistir na entidade Room ou no JSON de trabalho até a confirmação explícita do usuário.
    * **Barra Inferior Fixa (Footer de Navegação Rápida)**:
      - Inserido rodapé fixo ao final do diálogo estilizado com os botões `[-]` (anterior) e `[+]` (próximo), permitindo audição sequencial ágil entre timbres da categoria ou filtro atual com auto-scroll da `LazyColumn`.
      - Visor digital central com indicador LED ciano "OUVINDO (AUDITION)", nome da voz ativa, banco e número de programa.
      - Botão de ação primária `[OK]` (`confirmVoiceSelection`) que grava definitivamente a voz selecionada na Parte e persiste o estado de trabalho.
    * **Cancelamento e Reversão Automática**:
      - Ao tocar no botão fechar `[✕]` ou dispensar o modal clicando fora, o método `cancelVoiceSelection` reverte imediatamente para o timbre original que estava carregado antes da abertura do seletor, reenviando os comandos MIDI correspondentes ao sintetizador físico.
16. **Build 23 (v1.1.8-preview / Atualização) - Sincronização de Seleção de Partes no Mixer (Top Tabs P1..P16 -> Mixer Channels):**
    * **Foco Imediato de Parte Ativa ao Vivo (switchLiveFocusedPart)**:
      - Na visualização do Mixer, ao clicar em qualquer aba superior `P1..P16` (`ScrollableTabRow`) ou no visualizador de teclas (`SplitKeyboardVisualizer`), a respectiva Parte agora dispara imediatamente `switchLiveFocusedPart(partIndex)` no `PerformanceViewModel`.
      - Realiza Safety Flush, assegura Link 1-2 desligado no hardware, acopla a Parte ao Canal 1 (teclas físicas do teclado), transmite sequência segura SysEx de mudança de canal de recepção e atualiza o banner `"🎹 Ao Vivo: Part X [Voice] no teclado"`.
      - O card correspondente no Mixer recebe automaticamente o destaque visual `P X ATIVA` com borda e background em `YamahaCyan`.
    * **Auto-Scroll Suave para o Canal do Mixer (LazyRow / LazyListState)**:
      - Refatoradas as faixas de canais de áudio no `MixerOverviewScreen.kt` de `Row` com `.horizontalScroll` para `LazyRow` com `lazyListState = rememberLazyListState()` tanto no modo Retrato (Portrait) quanto no modo Palco Paisagem (Landscape).
      - Implementado `LaunchedEffect(activeFocusedPartIndex, selectedPartIndex)` disparando animação suave de rolagem (`lazyListState.animateScrollToItem(targetIndex)`), garantindo que o fader e a coluna do canal selecionado ou focado fiquem imediatamente visíveis no centro/área visível da tela sem necessidade de rolagem manual.
17. **Build 24 (v1.1.8) - Publicação na Google Play Console:**
    * Incremento para `versionCode = 24` e `versionName = "1.1.8"` para resolver a duplicidade do código de versão 23 já utilizado anteriormente na Play Console.
    * Consolidação de todas as melhorias da Build 23: modo de pré-escuta (Audition/Preview) com navegação rápida `[-]`/`[+]`, visor digital "OUVINDO (AUDITION)", reversão automática no cancelamento/dismiss, sincronia imediata das abas `P1..P16` no Mixer e auto-scroll horizontal dos faders com `LazyRow`.
    * Compilação dos novos pacotes assinados de produção (`app-release.aab` e `app-release.apk`).
18. **Build 25 (v1.1.9) - Camada Híbrida de Conexão MIDI com Driver USB Host Direto (Fallback Yamaha MX V1 - Vendor ID 0x0499):**
    * **Diagnóstico e Motivação Técnica**:
      - O Yamaha MX V2 possui chip USB Class-Compliant nativo reconhecido pelo `android.media.midi.MidiManager`.
      - O Yamaha MX V1 utiliza descritores USB proprietários da Yamaha (Vendor ID `0x0499` / 1177) e interfaces legadas que o subsistema nativo do Android não enumera como porta MIDI padrão.
    * **Camada de Abstração de Transporte (`MidiTransport`)**:
      - Criada a interface unificada `MidiTransport` com `connect()`, `disconnect()`, `sendBytes()`, `registerReceiver()`, `isConnected()` e `getDeviceName()`.
      - O `MidiConnectionManager` consome `MidiTransport`, mantendo ViewModels, faders, SysEx e throttler 100% agnósticos ao tipo de transporte físico.
    * **Driver USB Host Direto em Espaço de Usuário (`YamahaUsbDirectDriver`)**:
      - Conecta diretamente com o hardware Yamaha via `android.hardware.usb.UsbManager` com requisição de permissão sob demanda (`ACTION_USB_PERMISSION`).
      - Descoberta dinâmica de endpoints: prioriza interface de Áudio/Streaming (Classe 1, Subclasse 3) ou qualquer interface alternativa/proprietária (Classe 0xFF) contendo endpoints Bulk IN e Bulk OUT.
      - Reivindica interface com `claimInterface(usbInterface, true)` para controle exclusivo.
      - **Thread de Leitura Contínua (Worker Thread)**: Efetua loop de leitura em `bulkTransfer` no endpoint Bulk IN, decodifica pacotes USB-MIDI padrão de 4 bytes (CIN) e despacha o payload bruto diretamente para o parser do app (`processIncomingMidi`), preservando telemetria de notas, sustentação e LEDs.
      - **Envio de Dados USB-MIDI**: Conversão bidirecional via `UsbMidiPacketConverter` empacotando notas, CC, pitch bend e mensagens SysEx (fragmentação com CIN 0x04..0x07) em blocos de 4 bytes transmitidos via `bulkTransfer` no endpoint Bulk OUT.
    * **Driver Nativo (`AndroidMidiTransport`)**:
      - Encapsula o `android.media.midi.MidiManager` original garantindo 100% de retrocompatibilidade para o Yamaha MX V2 e controladores genéricos.
    * **Orquestrador de Fallback Automático (`MidiConnectionManager`)**:
      - 1º: Tenta conectar via `AndroidMidiTransport` nativo.
      - 2º: Se nenhum dispositivo nativo responder em 1.5s ou se houver falha de abertura de porta, busca por dispositivo UsbDevice com Vendor ID `0x0499`.
      - 3º: Solicita permissão USB (se necessário) e conecta automaticamente via `YamahaUsbDirectDriver` com status visual `"Yamaha MX Series (Modo USB Direto)"`.
      - Ao desconectar o cabo OTG, libera interface, fecha descritores e reseta o estado de forma segura.
    * **Suíte de Testes Automatizados**:
      - Criada suíte unitária em `UsbMidiPacketConverterTest.kt` validando Note On, Note Off, CC, Program Change, Pitch Bend, mensagens em tempo real e pacotes SysEx com comprimentos variados (7, 8, 9 e 10 bytes), aprovada com 100% de sucesso.
19. **Build 26 (v1.1.10) - Padronização da Notação de Notas Musicais (Middle C = C4) e Novos Presets de Split (MX61 / MX88):**
    * **Migração para Notação Científica Internacional de Altura (Middle C = C4)**:
      - Atualizado o cálculo em `NoteHelper.kt` de `(note / 12) - 2` (convenção legada Yamaha/Cubase onde MIDI 60 = C3) para `(note / 12) - 1` (padrão internacional de acústica, teoria musical e pianos universais onde MIDI 60 = C4).
      - Elimina confusão visual com contagens negativas invertidas nos limites inferiores:
        * MIDI 0 passa a ser `C-1` (em vez de `C-2`).
        * MIDI 12 passa a ser `C0`.
        * MIDI 21 passa a ser `A0` (tecla física mais grave de pianos de 88 teclas / MX88).
        * MIDI 36 passa a ser `C2` (tecla física mais grave do MX61 sem transposição).
        * MIDI 59 passa a ser `B3` (fim do split de mão esquerda/Lower).
        * MIDI 60 passa a ser `C4` (Dó Central / Middle C).
        * MIDI 69 passa a ser `A4` (Lá 440 Hz).
        * MIDI 96 passa a ser `C7` (tecla física mais aguda do MX61 sem transposição).
        * MIDI 108 passa a ser `C8` (tecla física mais aguda de pianos de 88 teclas / MX88).
        * MIDI 127 passa a ser `G9` (limite superior MIDI).
    * **Reformulação dos Presets de Split no Seletor de Faixa de Notas (`NoteRangeSelector.kt`)**:
      - Botões rápidos organizados ergonomicamente em duas linhas para melhor visualização e toque em telas de celular:
        * Linha 1: `Full (C-1..G9)` [0..127], `Lower (C-1..B3)` [0..59], `Upper (C4..G9)` [60..127].
        * Linha 2: `MX61 (C2..C7)` [36..96] e novo preset `MX88 (A0..C8)` [21..108].
    * **Padronização Visual com Delimitador `..`**:
      - Atualizada a formatação dos limites de nota nos cards e tiras de canal (`PartStripItem.kt` e `NoteRangeSelector.kt`) utilizando `..` (ex: `C-1..G9`, `C2..C7`) em vez do hífen simples `-`, eliminando ambiguidade visual com o sinal de menos das oitavas negativas (evitando `"C-1-G9"`).
    * **Suíte de Testes Automatizados**:
      - Atualizado `NoteHelperTest.kt` cobrindo 100% dos pontos de referência críticos: C-1 (0), C0 (12), A0 (21), C1 (24), C2 (36), C3 (48), B3 (59), C4 (60), A4 (69), C7 (96), C8 (108) e G9 (127), validado com 100% de aprovação.
20. **Build 27 (v1.1.11) - Presets de Split com Seletor de Modelo Físico (MX49, MX61, MX88 e Full MIDI) e Divisão Geométrica:**
    * **Novo Modelo de Domínio (`KeyboardModel.kt`)**:
      - Criado enum `KeyboardModel` com a extensão física exata e as metades geométrica/simétricas (Lower = 1ª metade / Upper = 2ª metade) na convenção internacional Middle C = C4:
        * **MX49 (49 teclas: C3 a C7 / notas 48 a 96)**:
          - Full: `C3 (48) .. C7 (96)` [49 teclas]
          - Lower: `C3 (48) .. B4 (71)` [24 teclas - 1ª metade]
          - Upper: `C5 (72) .. C7 (96)` [25 teclas - 2ª metade]
        * **MX61 (61 teclas: C2 a C7 / notas 36 a 96)**:
          - Full: `C2 (36) .. C7 (96)` [61 teclas]
          - Lower: `C2 (36) .. F4 (65)` [30 teclas - 1ª metade]
          - Upper: `F#4 (66) .. C7 (96)` [31 teclas - 2ª metade]
        * **MX88 (88 teclas: A0 a C8 / notas 21 a 108)**:
          - Full: `A0 (21) .. C8 (108)` [88 teclas]
          - Lower: `A0 (21) .. E4 (64)` [44 teclas - 1ª metade exata 50%]
          - Upper: `F4 (65) .. C8 (108)` [44 teclas - 2ª metade exata 50%]
        * **Full MIDI (Extensão total MIDI: C-1 a G9 / notas 0 a 127)**:
          - Full: `C-1 (0) .. G9 (127)` [128 notas]
          - Lower: `C-1 (0) .. B3 (59)` [60 notas - metade inferior]
          - Upper: `C4 (60) .. G9 (127)` [68 notas - metade superior]
    * **Arquitetura Visual e Reatividade (`NoteRangeSelector.kt`)**:
      - **Linha 1 (Seletor de Modelo)**: Segmented Control / Chips horizontais compactos `[ MX49 ] [ MX61 ] [ MX88 ] [ Full MIDI ]` com destaque visual (ciano) no modelo ativo e persistência entre sessões em `SharedPreferences` (`yamaha_mx_prefs` -> `selected_keyboard_model`, padrão: `MX61`).
      - **Linha 2 (Ações Contextuais de Split)**: 3 botões dinâmicos (`Full`, `Lower`, `Upper`) cujos rótulos e intervalos adaptam-se imediatamente ao modelo selecionado (ex: ao selecionar MX61, os botões tornam-se `Full (C2..C7)`, `Lower (C2..F4)`, `Upper (F#4..C7)`).
      - Destaque visual automático (`isSelected`) quando os limites atuais da Parte coincidirem com qualquer um dos presets do modelo ativo.
      - Ao tocar no botão, define imediatamente `noteLimitLow` e `noteLimitHigh` e dispara atualização via `MidiThrottler` para o teclado físico.
    * **Suíte de Testes Automatizados**:
      - Criado `KeyboardModelTest.kt` validando limites físicos, contagens de teclas, contiguidade perfeita entre Lower e Upper, e geração exata dos rótulos dinâmicos para todos os modelos, 100% aprovado.
21. **Build 29 (v1.1.13) - Correção Crítica de ANR (MIDI Assíncrono), Minificação R8 e minSdk 24:**
    * **Eliminação de ANR no Envio MIDI (`AndroidMidiTransport.kt`)**:
      - **Causa Raiz**: O Google Play Console registrou ocorrências de `Input dispatching timed out in com.yamahamx.editor.data.midi.AndroidMidiTransport.sendBytes` causadas pela execução síncrona de `MidiInputPort.send` na Main Thread durante eventos de UI e rajadas de mensagens (como Safety Flush / Panic enviando até 48 comandos em sequência).
      - **Solução com FIFO Channel**: Implementado trabalhador dedicado em background executando em `Dispatchers.IO` alimentado por um `Channel<ByteArray>(Channel.UNLIMITED)`.
      - **Garantias**:
        * A chamada `sendBytes(bytes)` na Main/UI thread agora apenas enfileira o array de bytes (`bytes.clone()`) no Channel, retornando imediatamente em microssegundos sem nenhum bloqueio de I/O de hardware.
        * A ordem estrita (FIFO) de todas as mensagens MIDI e comandos SysEx (ex: Bank MSB -> LSB -> Program Change, flushes de canais) é 100% preservada pelo consumidor sequencial.
        * Tratamento resiliente de `IOException` e desconexões físicas abruptas de cabos USB OTG.
        * Limpeza e cancelamento seguro do worker e do canal no método `disconnect()`.
    * **Otimização DEX e Minificação R8 (`app/build.gradle.kts` e `app/proguard-rules.pro`)**:
      - Habilitados `isMinifyEnabled = true` e `isShrinkResources = true` para a variante `release`.
      - Regras de ProGuard adicionadas para proteger contra ofuscação/remoção indevida:
        * Classes de modelo MIDI, repositórios e banco de dados: `-keep class com.yamahamx.editor.data.** { *; }`
        * Callbacks de hardware do Android MIDI (`OnDeviceOpenedListener`, `MidiReceiver`).
        * ViewModels e APIs do Google Play Billing e In-App Updates.
      - Redução do tamanho final do binário (AAB com ~3.2 MB e APK com ~2.0 MB) com 100% de otimização DEX.
    * **Elevação de API Mínima (minSdk 24)**:
      - Atualizado `minSdk` de 23 para 24 (Android 7.0 Nougat) no `app/build.gradle.kts` para atender aos requisitos da Proteção Automática do Google Play Console.
    * **Versionamento**: `versionCode = 29`, `versionName = "1.1.13"`.
22. **Padronização de Screenshots para a Google Play Store (`PlayStore_Ready`)**:
    * **Automação via Pillow (`process_screenshots.py`)**: Script em Python para remoção pixel-a-pixel dos elementos do Android (barra de status e barra gestual inferior) e enquadramento nas resoluções oficiais do Google Play Console sem distorção.
    * **Assets Prontos para Publicação**:
      - **Celular (Vertical - 1080 x 2400 / 9:20)**:
        * `01_mixer_celular.png`: Mixer de 4 Canais (Volume, Pan, Mute, Solo, Modulação LED).
        * `02_setlist_celular.png`: Setlist, busca rápida e troca instantânea de timbres.
        * `03_parte_efeitos_celular.png`: Edição detalhada de parte, efeitos e transposição.
        * `04_filtro_split_celular.png`: Filtros de síntese (Cutoff/Resonance) e presets de Keyboard Split.
      - **Tablet / Paisagem (Horizontal - 1920 x 1080 / 16:9)**:
        * `05_mixer_tablet_landscape.png`: Visão expandida com 8 faders horizontais simultâneos.
    * **Formato**: PNG 24-bit RGB (sem canal alpha) com preenchimento nas bordas na cor oficial do tema escuro (`#121820`).
    * **Diretório**: `Imagens MX  Control\PlayStore_Ready`.
23. **Build 30 (v1.1.14) - Auditoria de Desempenho e Subamostragem de Bitmaps (`BitmapDownsampler` e Alerta `I0.a.c`):**
    * **Diagnóstico de Bytecode (De-obfuscação)**:
      - Rastreamento no `mapping.txt` (18 MB) e no `classes.dex` revelou que o alerta `I0.a.c (falta o parâmetro BitmapFactory.Options)` do Play Console correspondia a `androidx.core.graphics.drawable.IconCompat$Api23Impl.toIcon`, que executa `BitmapFactory.decodeStream(is)` sem opções na biblioteca AndroidX Core.
      - No código do app, essa dependência era invocada por `NotificationCompat.Builder` em `MidiForegroundService.kt`.
    * **Refatoração do Serviço (`MidiForegroundService.kt`)**:
      - Substituído `NotificationCompat.Builder` pelo `android.app.Notification.Builder` nativo do framework (compatível diretamente com `minSdk = 24`), desacoplando o serviço da camada legada do `IconCompat`.
    * **Novo Módulo Utilitário de Downsampling (`BitmapDownsampler.kt`)**:
      - Implementado utilitário defensivo seguindo as diretrizes oficiais do Android para decodificação em duas etapas:
        * Leitura leve de metadados (`inJustDecodeBounds = true`) sem alocar memória de pixels na JVM.
        * Cálculo dinâmico do `inSampleSize` como potência de 2 baseado nos limites máximos da tela.
        * Decodificação segura (`inJustDecodeBounds = false`) com tratamento de `OutOfMemoryError` para Resources, Arquivos, ByteArrays e Streams.
      - Criada suíte de testes unitários dedicada em `BitmapDownsamplerTest.kt` com 100% de aprovação.
    * **Arquitetura UI**: A interface do MX Control opera 100% em Jetpack Compose com gráficos vetoriais nativos (`ImageVector`, `Canvas`), sem sobrecarga de memória por bitmaps.
    * **Versionamento**: `versionCode = 30`, `versionName = "1.1.14"`.
24. **Build 33 (v1.2.1) - Forçamento Global de FontScale (1.0f), Layout Defensivo e Modulação Contínua na Aba Parte:**
    * **Travamento de FontScale em Nível Raiz**:
      - **Compose Root (`MainActivity.kt` & `Theme.kt`)**: Envelopamento no nível raiz com `CompositionLocalProvider(LocalDensity provides Density(density = currentDensity.density, fontScale = 1f))` tanto em `setContent` quanto no tema `YamahaMXEditorTheme`.
      - **Activity Base Context (`MainActivity.kt`)**: Implementado `attachBaseContext` criando `ConfigurationContext` com `config.fontScale = 1.0f`, garantindo que toda a hierarquia de janelas, métricas de display e subcompositions do Android iniciem com escala de fonte fixa em 1.0f, imune às configurações de acessibilidade do sistema operacional.
    * **Proteção Defensiva Anti-Quebra e Truncamento de Textos (`maxLines = 1`, `softWrap = false`)**:
      - **Header de Conexão (`ConnectionHeader.kt`)**: Botão PANIC e SYNC agora contam com `.defaultMinSize(minWidth = 1.dp, minHeight = 1.dp)` e `maxLines = 1, softWrap = false`, impedindo que o texto "PANIC" quebre em múltiplas linhas e exiba apenas "P" em telas compactas.
      - **Badge de Polifonia / Notas (`ConnectionHeader.kt`)**: Aplicados `maxLines = 1` e `softWrap = false` aos rótulos `NOTAS` e contador `%d/128`.
      - **Chips de Categoria na Setlist (`SetlistScreen.kt`)**: `CategoryFilterChip` e botões de ação ("Salvar Atual", "Backup JSON") protegidos com `maxLines = 1` e `softWrap = false`.
      - **Seletor de Timbres (`VoiceSelectorDialog.kt`)**: Chips de categoria do `LazyRow` configurados com `maxLines = 1, softWrap = false`.
      - **Abas de Visualização (`PerformanceScreen.kt`)**: Chips `PARTE`, `MIXER` e `SETLIST` protegidos contra quebra indesejada.
      - **Seletor de Faixa de Notas (`NoteRangeSelector.kt`)**: Chips de modelos físicos (MX49, MX61, MX88, Full MIDI) e botões de split (Full, Lower, Upper) com `softWrap = false`.
      - **Tiras de Canal do Mixer (`PartStripItem.kt`)**: Header da parte, pílula de timbre e botões MUTE e SOLO protegidos com `defaultMinSize` e `maxLines = 1, softWrap = false`.
    * **Controle Contínuo de Modulação / Rotary Speaker na Aba "Parte" (`PartDetailScreen.kt`)**:
      - **Eliminação do Toggle Binário**: Removido o botão antigo com rótulo fixo "SLOW (0) / FAST (127)" que truncava para "SLO" em telas menores e causava saltos bruscos.
      - **Unificação com a Interface do Mixer**: Implementado visor digital estilo LED mostrando `MOD: <valor>` (0 a 127) em âmbar ou `BYPASS` em vermelho quando a recepção está desligada.
      - **Ajuste Gradual e Fino**: Slider horizontal contínuo (`valueRange = 0f..127f`), botões de passo `[-]` e `[+]` para micro-ajustes, botão de reset rápido `[0]` e suporte a toque/duplo-toque no visor para zerar instantaneamente.
      - **Transmissão Gradual MIDI CC 1**: Ao arrastar o slider ou tocar nos controles de passo, o valor é transmitido continuamente via CC 1 com proteção do `MidiThrottler` (20ms) para a parte ativa.
    * **Versionamento**: `versionCode = 33`, `versionName = "1.2.1"`.
25. **Build 34 (v1.2.2) - Compatibilidade Responsiva no Cabeçalho da Parte Ativa (`PartDetailScreen.kt`):**
    * **Prevenção de Compressão em Telas Estreitas**:
      - Em dispositivos com resolução horizontal mais compacta, o botão SOLO na extremidade direita do card da parte ativa podia ser espremido contra a borda devido à expansão natural do bloco de título.
      - **Bloco Central (`Column` com Título e Subtítulo MIDI Rx)**: Configurado com `Modifier.weight(1f, fill = false).padding(horizontal = 4.dp)` e `overflow = TextOverflow.Ellipsis`. Dessa forma, o bloco ocupa o espaço disponível sem exceder os limites e sem forçar os demais elementos contra as bordas.
      - **Botões MUTE e SOLO**: Garantida proteção total contra compressão com `Modifier.defaultMinSize(minWidth = 48.dp)` e `maxLines = 1, softWrap = false`, assegurando legibilidade perfeita em qualquer proporção de tela.
      - **Margem de Respiro Lateral**: Ajustado o padding da Row para `padding(horizontal = 8.dp, vertical = 8.dp)`, assegurando margem de respiro segura em relação às bordas do Card.
    * **Versionamento**: `versionCode = 34` (Build inicial) e `versionCode = 35` (Build de publicação Play Console), `versionName = "1.2.2"`.
26. **Build 44 (v1.3.7) - Suporte Completo ao Idioma Espanhol (i18n) e Paridade de Recursos Pro:**
    * **Localização para Espanhol (`res/values-es/strings.xml`)**:
      - Implementado arquivo completo de recursos de string para usuários hispanofalantes.
      - Terminologia técnica e musical precisa para sintetizadores e teclados: *Mezclador*, *Vista General del Teclado (Splits & Layers)*, *Límite de Notas*, *Transposición*, *Resonancia*, *Cutoff*, *Reverb Send*, *Chorus Send*, *Emparejamiento BLE*, etc.
      - Tradução integral do Paywall Pro, Diálogos de Salvamento e Backup JSON, Seletor de Timbres (Voices), Categorias de Instrumentos e Toasts informativos.
      - Adicionado suporte a strings de promoção Black Friday / Semana Especial Pro (`promo_black_friday_title` e `promo_black_friday_desc`).
      - Paridade de chaves assegurada entre `values/strings.xml`, `values-pt-rBR/strings.xml` e `values-es/strings.xml`.
    * **Versionamento de Lançamento**: Atualizado para `versionCode = 44` e `versionName = "1.3.7"`.

---

## 5. Engenharia Reversa do Vycro MX (Yamaha SysEx)

Toda a documentação técnica foi consolidada em [`docs/VYCRO_SYSEX_MAPPING.md`](file:///c:/Users/alexp/Documents/APP%20MX61/docs/VYCRO_SYSEX_MAPPING.md).

### Resumo dos Resultados:
* **Model ID**: `0x7F 0x17` (Yamaha MX49/MX61).
* **Estrutura de Mensagem**: `F0 43 10 7F 17 [AddrHigh] [AddrMid] [AddrLow] [Data...] F7`.
* **Mapa de Memória**:
  * `0x36 0x00 0x00`: Multi Common (Nome da Performance em 10 chars ASCII, Layer Switch).
  * `0x36 0x01 0x00`: Reverb Master Block (Tipo, Tempo, Retorno, Pan).
  * `0x36 0x02 0x00`: Chorus Master Block.
  * `0x37 [00..0F] 0x00`: Multi Part 1 a 16 (Volume `0x0E`, Pan `0x0F`, Cutoff `0x2D`, Resonance `0x2E`, ADSR Envelopes `0x19..0x1C`, 3-Band EQ `0x23..0x29`, Note Limits `0x08..0x0B`).
  * `0x38 [00..0F] 0x00`: Insertion Effects dedicados por parte.
* **Bulk Dump Request da Performance Ativa**:
  1. `F0 43 00 7F 17 00 00 0E 5F 00 13 F7`
  2. `F0 43 20 7F 17 0E 5F 00 F7`
* **Fórmula do Checksum**: `(0x80 - (Sum(bytes_a_partir_do_byte5) & 0x7F)) & 0x7F`.

---

## 6. Auditoria Técnica Recente: Modulação (CC 1)

* **Problema Relatado**: Fader de modulação (CC 1) não altera o som em alguns timbres.
* **Resultado da Auditoria**:
  1. **Codificação**: O método `YamahaSysExEncoder.createControlChangeMessage` está 100% correto (`0xBn, 0x01, value`).
  2. **Endpoint USB**: É enviado exatamente na mesma fila `MidiThrottler` e porta física USB usada para Volume e Pan.
  3. **Causa Raiz Identificada no Yamaha MX**:
     * Volume e Pan no app funcionam sempre porque utilizam **SysEx de Multi Part (`0x37`)**, enquanto Modulação utiliza **MIDI CC 1**.
     * No Yamaha MX, o CC 1 só altera velocidade de Rotary Speaker se o timbre de órgão tiver o **Controller Set** configurado de fábrica com `MW -> EffA/B (Rotary Speed)`. Timbres de piano, pads ou órgãos sem essa atribuição respondem apenas com vibrato de LFO ou ignoram o CC 1.
     * Além disso, as teclas físicas do MX tocam na **Parte 1 (Canal 1)**. Se o órgão estiver na Parte 2 e o usuário mexer no fader da Parte 2 sem ligar o Layer, o CC 1 vai para o Canal 2 enquanto o som ouvido é da Parte 1.

---

## 7. Roadmap de Próximas Implementações

### Prioridade 1 (Curto Prazo - Após aprovação da v1.1.3 na Play Store):
* [ ] **Sincronização Bidirecional (Bulk Dump Receive)**:
  * Enviar requisição de dump (`0x0E 0x5F 0x00`) ao conectar USB ou tocar em "Sincronizar".
  * Ler os 37 pacotes e atualizar automaticamente no app os 16 timbres, volumes, pans e split points reais do teclado físico.
* [ ] **Controle Direto de Rotary via Insertion Effect SysEx**:
  * Em vez de depender de CC 1, disparar o parâmetro de velocidade diretamente no bloco de efeito de inserção (`0x38`).

### Prioridade 2 (Médio Prazo - Edição Sonora Avançada):
* [ ] **Controle de Envelopes ADSR**: Sliders de Attack (`0x19`), Decay (`0x1A`), Sustain (`0x1B`) e Release (`0x1C`) na tela "Parte".
* [ ] **Equalizador Paramétrico de 3 Bandas**: Ajuste de graves, médios (com Q) e agudos por canal (`0x23..0x29`).
* [ ] **Controle de Dinâmica (Velocity Switching)**: Limites de toque suave/forte (`0x0A` e `0x0B`).
* [ ] **Renomear Performance no LCD**: Campo de texto para mudar o nome da performance no visor digital do MX (`0x36 0x00 0x00`).

### Prioridade 3 (Longo Prazo - Modo Palco e Backup):
* [ ] **Modo Palco / Live View**: Tela minimalista de alto contraste com fontes grandes (Música Atual, Próxima, Tom, BPM).
* [ ] **Suporte a Pedais Bluetooth (Page Turner)**: Troca de músicas e efeitos via pedais sem as mãos.
* [ ] **Visualizador de Cifras e Letras**: Integrado à Setlist com rolagem automática.
* [ ] **Backup e Restauração em Nuvem (.json / Google Drive)**: Salvar e restaurar repertórios completos.

---

## 8. Regras de Conduta para o Antigravity (Multi-Máquina)

1. **Leitura Prévia Obrigatória**: Sempre consulte este arquivo antes de sugerir ou executar mudanças na arquitetura.
2. **Atualização Contínua**: Ao concluir qualquer alteração de código ou configuração, registre aqui o novo estado, nova versão ou novo comportamento.
3. **Preservação de Estabilidade**: Nunca altere a base de código quando houver versão em avaliação na Google Play sem instrução explícita do usuário.
