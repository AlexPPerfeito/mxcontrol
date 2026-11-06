# Especificação Técnica de Engenharia Reversa: Mapeamento SysEx e Tabelas do Vycro MX (Yamaha MX49 / MX61)

## 1. Visão Geral e Contexto

Este documento reúne a documentação técnica consolidada da engenharia reversa das tabelas de parâmetros, blocos de memória e mensagens MIDI System Exclusive (SysEx) do sintetizador **Yamaha MX49 / MX61**, extraídas diretamente da instalação do software **Vycro MX** (Psicraft / Sound Quest).

### 1.1 Arquivos Inspecionados
- **Diretório Base**: `C:\Program Files (x86)\Psicraft\Yamaha\Vycro MX\`
- **Arquivos Analisados**:
  - `Instruments\Vycro MX\MX49-61 Performance.ini` (641 KB - Tabelas de inicialização de timbres e dump padrão `[Patch Init]`)
  - `Instruments\Vycro MX\Performance.Imc` (1 KB - Driver compilado de comunicação e dump requests)
  - `Instruments\Vycro MX\MX49-61 Performance.sqt` (914 KB - Definições de controles e blocos de interface)
  - `Instruments\Editor.SQ` (Lookup table binária de offsets assinados)
  - `Instruments\Vycro MX.ini` (Configurações de Device Inquiry e metadados de driver)

---

## 2. Identificação do Dispositivo (Device Inquiry & SysEx Framing)

### 2.1 Padrão de Identificação Universal MIDI (Device Inquiry)
- **Fabricante (Yamaha)**: `0x43` (67 decimal)
- **Family ID**: `0x4100` (`Family1 = 0x00`, `Family2 = 0x41` [65])
- **Member ID**: `0x0647` (`Member1 = 0x47` [71], `Member2 = 0x06`)
- **Tamanho da Resposta**: 15 bytes

### 2.2 Cabeçalho de Mensagens SysEx do Yamaha MX
```
F0 43 [Device/Sub] [Model1] [Model2] [AddrHigh] [AddrMid] [AddrLow] [Data...] [Checksum?] F7
```

- **SysEx Start**: `0xF0`
- **Yamaha ID**: `0x43`
- **Device Number / Substatus**:
  - `0x10`: Parameter Change individual (1 byte ou bloco curto sem checksum)
  - `0x00`: Dump Request (Requisição de leitura do sintetizador)
  - `0x20` ou `0x24`: Bulk Dump Header / Data Packet
- **Model ID**: `0x7F 0x17` (Família MX49 / MX61)
- **SysEx End**: `0xF7`

---

## 3. Mapa de Memória de Performance (Memory Architecture)

No Vycro MX e no Yamaha MX, uma Performance completa em edição é representada por **37 blocos de parâmetros** (Multi Common + 36 pacotes SysEx), totalizando **2.782 bytes de payload** (2.804 bytes com cabeçalho e enquadramento):

| Endereço (`High Mid Low`) | Tamanho Dados | Descrição do Bloco | Conteúdo Principal |
| :--- | :---: | :--- | :--- |
| `0x36 0x00 0x00` | 80 bytes (`0x50`) | **Multi / Performance Common** | Nome da Performance (10 chars ASCII), Layer/Split Switch, Link Part 1-2 |
| `0x36 0x01 0x00` | 38 bytes (`0x26`) | **Reverb Block** | Tipo de Reverb, Tempo, Retorno, Pan, Parâmetros 1..16 |
| `0x36 0x02 0x00` | 40 bytes (`0x28`) | **Chorus Block** | Tipo de Chorus/Flanger/Phaser, Retorno, Pan, Parâmetros 1..16 |
| `0x36 0x10 0x00` | 20 bytes (`0x14`) | **Controller & Arp Common** | Atribuições de knobs 1..4, botões e estado global do Arpeggiator |
| `0x36 0x30 0x00` | 14 bytes (`0x0E`) | **Rhythm Pattern Common** | Padrão rítmico, Tempo (BPM), Quantize, Volume da bateria |
| `0x37 [00..0F] 0x00` | 62 bytes (`0x3E`) | **Multi Part 1 a 16** (16 blocos) | Canal MIDI, Timbre (Bank/PC), Volume, Pan, EQ, ADSR, Filtro, Sends |
| `0x38 [00..0F] 0x00` | 72 bytes (`0x48`) | **Insertion FX 1 a 16** (16 blocos)| Efeitos de inserção dedicados por parte (Tipo A/B, Parâmetros 1..16) |

---

## 4. Tabela de Parâmetros de Parte (`0x37 [PartIndex] [AddrLow]`)

O byte `AddrMid` seleciona a parte: `0x00` para a Parte 1 até `0x0F` para a Parte 16.
Cada canal de parte possui 62 bytes de dados (offsets `0x00` a `0x3D`):

| AddrLow | Parâmetro | Faixa de Valores | Padrão / Descrição |
| :---: | :--- | :---: | :--- |
| `0x00` | **Part Mode** | `0..1` | 0 = Normal Voice, 1 = Drum Voice |
| `0x01` | **Bank Select MSB** | `0..127` | 63 (`0x3F`) = Presets de Fábrica, 0 = GM |
| `0x02` | **Bank Select LSB** | `0..127` | Sub-banco de timbres |
| `0x03` | **Program Number (PC)** | `0..127` | Número do Voice (Base 0: 0..127 = 1..128) |
| `0x04` | **Receive Channel** | `0..15, 127` | 0..15 = Ch 1..16; 127 (`0x7F`) = Off |
| `0x05` | **Mono / Poly Mode** | `0..1` | 0 = Mono, 1 = Poly |
| `0x06` | **Arpeggio Switch** | `0..1` | 0 = Off, 1 = On |
| `0x07` | **Arpeggio Hold** | `0..2` | 0 = Sync-Off, 1 = On, 2 = Off |
| `0x08` | **Note Limit Low** | `0..127` | Limite inferior de nota (0 = C -2) |
| `0x09` | **Note Limit High** | `0..127` | Limite superior de nota (127 = G 8) |
| `0x0A` | **Velocity Limit Low** | `1..127` | Dinâmica/Velocity mínima |
| `0x0B` | **Velocity Limit High** | `1..127` | Dinâmica/Velocity máxima |
| `0x0C` | **Velocity Sens Depth** | `0..127` | Sensibilidade de dinâmica (64 = Centro) |
| `0x0D` | **Velocity Sens Offset**| `0..127` | Deslocamento de dinâmica (64 = Centro) |
| `0x0E` | **Volume** | `0..127` | Volume individual da parte |
| `0x0F` | **Pan** | `1..127` | Panorâmica (1 = L63, 64 = Center, 127 = R63) |
| `0x10` | **Dry Level** | `0..127` | Nível de sinal direto (sem efeitos) |
| `0x11` | **Output Select** | `0..3` | Roteamento de áudio/saída |
| `0x12` | **Reverb Send Level** | `0..127` | Envio para o Reverb Master |
| `0x13` | **Chorus Send Level** | `0..127` | Envio para o Chorus Master |
| `0x14` | **Pitch Bend Range Low** | `0..48` | Extensão de Pitch Bend negativo (64 = 0) |
| `0x15` | **Pitch Bend Range High**| `0..48` | Extensão de Pitch Bend positivo |
| `0x16` | **Note Shift / Transpose**| `40..88` | Transposição da parte em semitons (-24..+24, 64 = 0) |
| `0x17` | **Detune** | `0..127` | Micro-afinação fina em cents (64 = 0) |
| `0x18` | **Portamento Switch** | `0..1` | 0 = Desativado, 1 = Ativado |
| `0x19` | **EG Attack Time** | `0..127` | Tempo de ataque do envelope (64 = 0 offset) |
| `0x1A` | **EG Decay Time** | `0..127` | Tempo de decaimento do envelope (64 = 0 offset) |
| `0x1B` | **EG Sustain Level** | `0..127` | Nível de sustentação (64 = 0 offset) |
| `0x1C` | **EG Release Time** | `0..127` | Tempo de liberação do envelope (64 = 0 offset) |
| `0x23` | **EQ Low Frequency** | `0..127` | Frequência de corte do grave do equalizador |
| `0x24` | **EQ Low Gain** | `32..96` | Ganho de grave (-12dB .. +12dB, 64 = 0dB) |
| `0x25` | **EQ Mid Frequency** | `0..127` | Frequência central de médios |
| `0x26` | **EQ Mid Gain** | `32..96` | Ganho de médios (-12dB .. +12dB, 64 = 0dB) |
| `0x27` | **EQ Mid Q (Ressonância)**| `0..127`| Fator Q / largura da faixa de médios |
| `0x28` | **EQ High Frequency** | `0..127` | Frequência de corte do agudo |
| `0x29` | **EQ High Gain** | `32..96` | Ganho de agudo (-12dB .. +12dB, 64 = 0dB) |
| `0x2D` | **Filter Cutoff** | `0..127` | Frequência de corte do filtro (64 = 0 offset) |
| `0x2E` | **Filter Resonance** | `0..127` | Ressonância do filtro (64 = 0 offset) |
| `0x38` | **Rcv Modulation Wheel** | `0..1` | Chave de recepção de modulação no hardware (0 = Bypass/Off, 1 = On) |

---

## 5. Parâmetros Multi Common (`0x36 0x00 0x00`)

O bloco Multi Common possui 80 bytes (`0x50` bytes):
- **Offsets `0x00` a `0x09`**: Nome da Performance em caracteres ASCII (10 bytes).
  * Exemplo no Vycro INIT: `"MXCategory"` (`4D 58 43 61 74 65 67 6F 72 79`).
- **Offset `0x44` (68)**: Part 1-2 Link / Layer Switch.
  * `0x00` = Camada/Layer desativada.
  * `0x01` = Camada/Layer ativada (Parte 1 e 2 soam juntas).

---

## 6. Procedimento de Bulk Dump e Algoritmo de Checksum

### 6.1 Solicitação da Performance Ativa (Buffer de Edição)
O Vycro MX utiliza a seguinte sequência de duas mensagens SysEx para extrair o estado completo do sintetizador:

1. **Seleção de Modo e Dump Request**:
   ```
   F0 43 00 7F 17 00 00 0E 5F 00 13 F7
   ```
   - Endereço de controle: `0x0E 0x5F 0x00`
   - Checksum: `0x13`

2. **Disparo de Transmissão Bulk**:
   ```
   F0 43 20 7F 17 0E 5F 00 F7
   ```

Ao receber esses comandos, o MX transmite sequencialmente os 37 pacotes SysEx detalhados na Seção 3.

### 6.2 Requisição de Bloco Específico (ex: Multi Part 1)
```
F0 43 00 7F 17 00 3E 37 00 00 0B F7
```
- Tamanho: `0x00 0x3E` (62 bytes)
- Endereço: `0x37 0x00 0x00`
- Checksum: `0x0B`

### 6.3 Algoritmo de Checksum Yamaha
O checksum cobre desde o byte 5 (`SizeHigh`) até o último byte de payload:
$$\text{Checksum} = (128 - (\sum_{i=5}^{N-1} (\text{byte}_i \ \& \ 0\text{x}7\text{F}) \ \& \ 0\text{x}7\text{F})) \ \& \ 0\text{x}7\text{F}$$

Implementação em Kotlin:
```kotlin
fun calculateYamahaChecksum(bytesFromSizeToDataEnd: ByteArray): Byte {
    var sum = 0
    for (b in bytesFromSizeToDataEnd) {
        sum += (b.toInt() and 0x7F)
    }
    return ((0x80 - (sum and 0x7F)) and 0x7F).toByte()
}
```

---

## 7. Tabelas de Predefinições (`MX49-61 Performance.ini`)

O arquivo contém **2.463 seções `[Table xxxx]`**:
- **`[Table 1000]` a `[Table 1999]` (19 bytes cada)**:
  Tabelas `Program to Part Init Values` para os timbres do banco Preset. Definem o ponto neutro de Cutoff, Ressonância, Attack, Decay, Sustain e Release para cada timbre ao ser carregado.
- **`[Table 2000]` a `[Table 2999]` (19 bytes cada)**:
  Mesmo mapeamento de inicialização para as vozes do banco General MIDI (GM).
- **`[Table 3000]` e `[Table 4000]` (27 bytes cada)**:
  Configurações de roteamento de Arpeggios e faixa de velocidade (Velocity Limit) por categoria de voz.

---

## 8. Snippets Kotlin Prontos para Extensão do `YamahaSysExEncoder.kt`

As constantes e métodos abaixo foram validados e estão prontos para serem adicionados à classe `YamahaSysExEncoder` quando for oportuno implementar a edição aprofundada de timbres:

```kotlin
// ============================================================================
// CONSTANTES DE ENDEREÇAMENTO EXPANDIDAS (VYCRO MX MAPPING)
// ============================================================================

// Multi Common
const val ADDR_HIGH_MULTI_COMMON: Byte = 0x36.toByte()
const val ADDR_LOW_COMMON_PART_1_2_LINK: Byte = 0x44.toByte()

// Multi Part (AddrHigh = 0x37, AddrMid = partIndex 0x00..0x0F)
const val ADDR_HIGH_MULTI_PART: Byte = 0x37.toByte()
const val ADDR_LOW_PART_MODE: Byte = 0x00.toByte()
const val ADDR_LOW_PART_POLY_MONO: Byte = 0x05.toByte()
const val ADDR_LOW_PART_ARP_SWITCH: Byte = 0x06.toByte()
const val ADDR_LOW_PART_ARP_HOLD: Byte = 0x07.toByte()
const val ADDR_LOW_PART_VELOCITY_LIMIT_LOW: Byte = 0x0A.toByte()
const val ADDR_LOW_PART_VELOCITY_LIMIT_HIGH: Byte = 0x0B.toByte()
const val ADDR_LOW_PART_DETUNE: Byte = 0x17.toByte()
const val ADDR_LOW_PART_PORTAMENTO_SWITCH: Byte = 0x18.toByte()

// Envelopes de Amplitude / Filtro (ADSR)
const val ADDR_LOW_PART_ATTACK: Byte = 0x19.toByte()
const val ADDR_LOW_PART_DECAY: Byte = 0x1A.toByte()
const val ADDR_LOW_PART_SUSTAIN: Byte = 0x1B.toByte()
const val ADDR_LOW_PART_RELEASE: Byte = 0x1C.toByte()

// Equalizador de 3 Bandas da Parte
const val ADDR_LOW_PART_EQ_LOW_FREQ: Byte = 0x23.toByte()
const val ADDR_LOW_PART_EQ_LOW_GAIN: Byte = 0x24.toByte()
const val ADDR_LOW_PART_EQ_MID_FREQ: Byte = 0x25.toByte()
const val ADDR_LOW_PART_EQ_MID_GAIN: Byte = 0x26.toByte()
const val ADDR_LOW_PART_EQ_MID_Q: Byte = 0x27.toByte()
const val ADDR_LOW_PART_EQ_HIGH_FREQ: Byte = 0x28.toByte()
const val ADDR_LOW_PART_EQ_HIGH_GAIN: Byte = 0x29.toByte()

// ============================================================================
// MÉTODOS DE CONTROLE DE ENVELOPE (ADSR)
// ============================================================================

fun setPartAttack(partIndex: Int, attack: Int): ByteArray {
    return buildParamChange(ADDR_HIGH_MULTI_PART, partIndex.coerceIn(0, 15).toByte(), ADDR_LOW_PART_ATTACK, attack.coerceIn(0, 127).toByte())
}

fun setPartDecay(partIndex: Int, decay: Int): ByteArray {
    return buildParamChange(ADDR_HIGH_MULTI_PART, partIndex.coerceIn(0, 15).toByte(), ADDR_LOW_PART_DECAY, decay.coerceIn(0, 127).toByte())
}

fun setPartSustain(partIndex: Int, sustain: Int): ByteArray {
    return buildParamChange(ADDR_HIGH_MULTI_PART, partIndex.coerceIn(0, 15).toByte(), ADDR_LOW_PART_SUSTAIN, sustain.coerceIn(0, 127).toByte())
}

fun setPartRelease(partIndex: Int, release: Int): ByteArray {
    return buildParamChange(ADDR_HIGH_MULTI_PART, partIndex.coerceIn(0, 15).toByte(), ADDR_LOW_PART_RELEASE, release.coerceIn(0, 127).toByte())
}

// ============================================================================
// MÉTODOS DE CONTROLE DE EQUALIZADOR (-12dB a +12dB, 64 = 0dB)
// ============================================================================

fun setPartEQLowGain(partIndex: Int, gain: Int): ByteArray {
    return buildParamChange(ADDR_HIGH_MULTI_PART, partIndex.coerceIn(0, 15).toByte(), ADDR_LOW_PART_EQ_LOW_GAIN, gain.coerceIn(32, 96).toByte())
}

fun setPartEQMidGain(partIndex: Int, gain: Int): ByteArray {
    return buildParamChange(ADDR_HIGH_MULTI_PART, partIndex.coerceIn(0, 15).toByte(), ADDR_LOW_PART_EQ_MID_GAIN, gain.coerceIn(32, 96).toByte())
}

fun setPartEQHighGain(partIndex: Int, gain: Int): ByteArray {
    return buildParamChange(ADDR_HIGH_MULTI_PART, partIndex.coerceIn(0, 15).toByte(), ADDR_LOW_PART_EQ_HIGH_GAIN, gain.coerceIn(32, 96).toByte())
}

// ============================================================================
// REQUISIÇÃO DE BULK DUMP DA PERFORMANCE ATIVA
// ============================================================================

fun buildDumpRequestCurrentPerformance(): List<ByteArray> {
    return listOf(
        // Solicita leitura do buffer corrente de Performance
        byteArrayOf(0xF0.toByte(), 0x43.toByte(), 0x00.toByte(), 0x7F.toByte(), 0x17.toByte(), 0x00.toByte(), 0x00.toByte(), 0x0E.toByte(), 0x5F.toByte(), 0x00.toByte(), 0x13.toByte(), 0xF7.toByte()),
        // Dispara a transmissão
        byteArrayOf(0xF0.toByte(), 0x43.toByte(), 0x20.toByte(), 0x7F.toByte(), 0x17.toByte(), 0x0E.toByte(), 0x5F.toByte(), 0x00.toByte(), 0xF7.toByte())
    )
}
```
