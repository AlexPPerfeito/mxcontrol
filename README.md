# MX Control — Yamaha MX49 / MX61 / MX88 Performance Controller

![Android](https://img.shields.io/badge/Platform-Android%208.0%2B-3DDC84?logo=android&logoColor=white)
![Kotlin](https://img.shields.io/badge/Language-Kotlin%201.9-7F52FF?logo=kotlin&logoColor=white)
![Compose](https://img.shields.io/badge/UI-Jetpack%20Compose-4285F4?logo=jetpackcompose&logoColor=white)
![MIDI](https://img.shields.io/badge/Protocol-Yamaha%20MX%20SysEx-00E5FF)
![Status](https://img.shields.io/badge/Build-Passing%20(Release)-00C853)

Aplicativo Android profissional desenvolvido em **Kotlin** e **Jetpack Compose** para gerenciamento, edição multitimbral, criação de splits/layers e mixagem ao vivo de performances nos sintetizadores **Yamaha MX49, MX61 e MX88** através de conexão direta **USB MIDI OTG**.

---

## 📥 Downloads Rápidos

| Arquivo | Descrição | Link Direto |
| :--- | :--- | :--- |
| 📦 **MXControl.apk** | Aplicativo Android compilado e assinado (Release v1.0) | [`MXControl.apk`](./MXControl.apk) *(~10.9 MB)* |
| 📦 **YamahaMXEditor.apk** | Cópia espelho do instalador APK | [`YamahaMXEditor.apk`](./YamahaMXEditor.apk) *(~10.9 MB)* |
| 📄 **Manual do Usuário (PDF)** | Guia completo de operação e referência de palco | [`Manual_do_Usuario_MX_Control.pdf`](./Manual_do_Usuario_MX_Control.pdf) |

---

## 💎 Versão Pro Vitalícia (Google Play In-App Purchase)

- **ID do Produto na Google Play**: `"mxcontrol_full_license"`
- **Versão Gratuita**: Partes 1 e 2 totalmente funcionais sem limites de tempo + até 2 slots de performances salvas no setlist.
- **Versão Pro Desbloqueada**:
  - Acesso irrestrito às **16 Partes Multitimbrais Simultâneas** com faders dedicados e mixagem independente.
  - **Setlists & Músicas Ilimitadas** para salvar todo o repertório de shows e ensaios.
  - **Layers de 2 a 16 timbres** com troca instantânea de foco no palco.
  - **Licença Vitalícia Única** vinculada à conta Google com restauração automática (`Restaurar Compra`).

---

## 🚀 Como Instalar e Usar no Palco

### 1. Instalação no Celular / Tablet:
1. Baixe o arquivo [`MXControl.apk`](./MXControl.apk) para o seu dispositivo Android.
2. Abra o arquivo no gerenciador de downloads e confirme a instalação (autorize *"Fontes Desconhecidas"* caso solicitado).

### 2. Conexão Física ao Sintetizador:
```
[Celular / Tablet Android]
         │ (Adaptador USB OTG)
         ▼
[Cabo USB Tipo A para Tipo B]
         │
         ▼
[Yamaha MX49 / MX61 / MX88 — Entrada USB TO HOST]
```

1. Ligue o teclado Yamaha MX.
2. No teclado, pressione `[UTILITY]` > `[MIDI]` e configure `MIDI IN/OUT = USB`.
3. Conecte o cabo USB do teclado no adaptador USB OTG do seu aparelho Android.
4. Abra o **MX Control** e confirme a permissão USB.
5. O aplicativo sincronizará automaticamente todas as 16 partes e o LED ficará **Verde**!

---

## 🎹 Principais Funcionalidades

### 1. Sincronização Completa de Vozes & Hardware (Auto-Sync)
- **Auto-Sync na Conexão**: Ao plugar o cabo USB, o estado completo das 16 partes (timbres, volumes, pans, envios de efeitos, filtros e faixas de split) é transmitido automaticamente para o gerador de som do teclado.
- **Disparo de Voz ao Focar Parte**: Ao tocar no card de qualquer parte, o aplicativo envia imediatamente a trinca SysEx / MIDI da Voice:
  1. `Bank Select MSB` (`0x01` / CC 0)
  2. `Bank Select LSB` (`0x02` / CC 32)
  3. `Program Change` (`0x03` / PC)
- **Botão Manual SYNC**: Reenvio forçado em lote de todas as 16 partes com throttling seguro de 15ms.

### 2. Modo Mesa de Som de Palco (Landscape / Horizontal)
- **Otimização Automática em Paisagem**:
  - Oculta gráficos secundários e barras de abas desnecessárias para uso em palco.
  - **100% da altura da tela** é dedicada aos 16 faders verticais com rolagem horizontal suave.
  - Faders de longo curso com `Modifier.weight(1f)` para controle táctil fino e preciso com os dedos.
- **Troca Rápida de Timbre no Mixer**: Toque direto no nome da Voice de qualquer canaleta para abrir o seletor de timbres e trocar o som instantaneamente durante a música.

### 3. LEDs Virtuais de Atividade MIDI em Tempo Real
- Cada faixa de canal possui um LED neon (`#00E5FF`) no topo que pisca em resposta a:
  - **Note On** (com velocidade > 0)
  - **Pedal de Sustain** (CC 64)
  - **Modulation Wheel** (CC 1)
  - **Pitch Bend** e Aftertouch
- Feedback visual imediato confirmando o canal MIDI que está recebendo notas do teclado físico.

### 4. Sistema Inteligente de Layers & Splits
- **Foco ao Vivo**: Toque simples em qualquer card para assumir o canal base do teclado (Ch 1 / `0x00`).
- **Layers Multitimbrais (2 a 16 partes)**: Toque longo (0,5s) para somar partes em layer com o som principal.
- **Desacoplamento Automático da Parte 1**: Ao criar layers com outras partes (ex: Parte 2 + Parte 3), a Parte 1 é automaticamente desvinculada (`Off`), evitando sons indesejados.
- **Visualizador Gráfico de Teclado**: Gráfico interativo que renderiza a extensão inteira do teclado (C-2 a G8) com faixas coloridas destacando os splits de cada parte.

### 5. Edição Completa dos Parâmetros das 16 Partes (Aba Parte)
- **Catálogo de 1.000+ Vozes de Fábrica** com busca rápida e filtros por 13 categorias (`AP`, `KB`, `OR`, `GT`, `BA`, `ST`, `BR`, `RP`, `LD`, `PD`, `CO`, `PC`, `DR`).
- **Volume da Parte** (0 a 127) com proteção contra inicialização em zero.
- **Panorâmica Estéreo** (L64 a R63) com botão de centralização rápida.
- **Envios de Efeitos**: Reverb Send (0 a 127) e Chorus Send (0 a 127).
- **Transposição / Note Shift**: Transposição cromática de -24 a +24 semitons.
- **Filtro VCF**: Frequência de corte (Cutoff) e Ressonância do filtro (-64 a +63).
- **Limites de Notas (Splits)**: Note Limit Low e High com notação musical padrão (C-2 a G8).
- **Roteamento de Canais MIDI**: Atribuição de canais de recepção individuais (Ch 1 a 16 ou Off).

### 6. Setlists & Troca Instantânea de Performances
- Crie e salve performances completas organizadas por música do seu repertório.
- Troca de performance no palco com **1 toque**, descarregando instantaneamente todas as 16 partes para o hardware.

### 7. Operação Contínua em Segundo Plano (Foreground Service)
- **MidiForegroundService**: Serviço de alta prioridade com notificação persistente na barra de status exibindo a performance e a parte ativa.
- **Prevenção de Encerramento**: Impede que o Android finalize o app ou interrompa a conexão MIDI durante o show.
- **WakeLock**: Mantém a tela ligada durante a execução da performance.

### 8. Botão de Emergência PANIC
- Silencia imediatamente notas presas através do envio simultâneo de:
  - *All Sound Off* (CC 120)
  - *All Notes Off* (CC 123)
  - *Reset All Controllers* (CC 121) nos 16 canais MIDI.

---

## 🗂️ Estrutura do Projeto

```
app/src/main/java/com/yamahamx/editor/
├── MainActivity.kt                      // Entrada principal, ciclo de vida e permissões USB
├── YamahaMxApp.kt                       // Classe da Aplicação
├── data/
│   ├── midi/
│   │   ├── YamahaSysExEncoder.kt        // Codificação SysEx nativa Yamaha MX (7F 17)
│   │   ├── MidiConnectionManager.kt     // Driver USB MIDI nativo e parser de eventos de canal
│   │   └── MidiThrottler.kt             // Fila de transmissão com coalescing e corrotinas
│   ├── model/
│   │   ├── YamahaVoice.kt               // Estrutura de Voices (MSB, LSB, PC, Categoria)
│   │   ├── MultiPartState.kt            // Estado das 16 partes (Volume, Pan, Splits, Efeitos)
│   │   ├── MultiCommonState.kt          // Parâmetros comuns (Part 1-2 Link)
│   │   ├── PerformanceState.kt          // Estado unificado da UI
│   │   ├── MidiDeviceInfoWrapper.kt     // Invólucro de dispositivos MIDI
│   │   └── NoteHelper.kt                // Conversor MIDI Note (0-127) para notação musical
│   ├── repository/
│   │   ├── VoiceRepository.kt           // Banco de dados com catálogo completo de timbres
│   │   └── PerformanceStorageRepository.kt // Persistência local de Setlists e configurações
│   └── service/
│       └── MidiForegroundService.kt     // Serviço em primeiro plano com notificação fixa
└── ui/
    ├── theme/                           // Tema Dark Synth Material 3 (Ciano, Dourado, Navy)
    ├── viewmodel/
    │   └── PerformanceViewModel.kt      // Gerenciamento reativo de estado (StateFlow)
    ├── components/
    │   ├── ConnectionHeader.kt          // Cabeçalho com status, switcher de modo, Sync e Panic
    │   ├── VoiceSelectorDialog.kt       // Modal de busca e filtro de timbres por categoria
    │   ├── SplitKeyboardVisualizer.kt   // Visualizador gráfico de teclado (Splits/Layers)
    │   ├── PartStripItem.kt             // Faixa de canal vertical (Mixer, fader e LED MIDI)
    │   ├── VerticalFader.kt             // Fader vertical de alta precisão
    │   ├── FaderControl.kt              // Fader horizontal com botões (+ / -)
    │   ├── PanControl.kt                // Knob/Slider bipolar de panorama
    │   ├── NoteRangeSelector.kt         // Seletor de faixas de split
    │   └── SavePerformanceDialog.kt    // Diálogo de salvamento de presets
    └── screens/
        ├── PerformanceScreen.kt         // Tela principal com abas e controle de orientação
        ├── PartDetailScreen.kt          // Tela de edição profunda da parte selecionada
        ├── MixerOverviewScreen.kt       // Modo Mixer e Mesa de Som de Palco
        └── SetlistScreen.kt             // Gerenciador de repertório e músicas
```

---

## 🛠️ Compilação e Testes

Para compilar o projeto a partir do código-fonte utilizando o Gradle Wrapper:

```bash
# Executar a suíte de testes unitários (25 testes)
./gradlew test

# Gerar o APK de Release assinado
./gradlew assembleRelease
```

O APK final será gerado em `app/build/outputs/apk/release/app-release.apk` e copiado para a raiz como `MXControl.apk`.

---

## 📜 Licença & Reconhecimentos

- Desenvolvido para tecladistas e produtores que utilizam a linha **Yamaha MX**.
- **Yamaha®**, **Motif®** e **MX49/MX61/MX88** são marcas registradas da *Yamaha Corporation*.
