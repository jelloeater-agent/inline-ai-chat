package com.github.roscrl.inlineaichat.settings

import com.intellij.icons.AllIcons
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.keymap.KeymapUtil
import com.intellij.openapi.options.Configurable
import com.intellij.openapi.ui.ComboBox
import com.intellij.openapi.ui.DialogPanel
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.ui.Messages
import com.intellij.ui.CollectionListModel
import com.intellij.ui.HyperlinkLabel
import com.intellij.ui.JBColor
import com.intellij.ui.SimpleListCellRenderer
import com.intellij.ui.ToolbarDecorator
import com.intellij.ui.components.*
import com.intellij.ui.components.JBPasswordField
import com.intellij.ui.components.JBTextArea
import com.intellij.ui.components.fields.ExtendableTextField
import com.intellij.ui.dsl.builder.AlignX
import com.intellij.ui.dsl.builder.panel
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import java.awt.BorderLayout
import java.awt.Dimension
import javax.swing.*

class InlineAIChatSettingsConfigurable : Configurable {
    private var settingsComponent: InlineAIChatSettingsComponent? = null

    override fun getDisplayName(): String = "Inline AI Chat Settings"

    override fun createComponent(): JComponent {
        settingsComponent = InlineAIChatSettingsComponent()
        reset() // Load the state into the component
        return settingsComponent!!.panel
    }

    override fun isModified(): Boolean {
        val component = settingsComponent ?: return false
        val settings = InlineAIChatSettingsState.instance
        return settings.apiBaseUrl != component.getApiBaseUrl() ||
                settings.openRouterApiKey != component.getOpenRouterApiKey() ||
                settings.selectedModel != component.getSelectedModel() ||
                settings.systemPrompt != component.getSystemPrompt()
    }

    override fun apply() {
        val component = settingsComponent ?: return
        val settings = InlineAIChatSettingsState.instance
        settings.apiBaseUrl = component.getApiBaseUrl()
        settings.openRouterApiKey = component.getOpenRouterApiKey()
        settings.selectedModel = component.getSelectedModel() ?: settings.selectedModel
        settings.systemPrompt = component.getSystemPrompt()
    }

    override fun reset() {
        val component = settingsComponent ?: return
        val settings = InlineAIChatSettingsState.instance
        component.setApiBaseUrl(settings.apiBaseUrl)
        component.setOpenRouterApiKey(settings.openRouterApiKey)
        component.setSelectedModel(settings.selectedModel)
        component.setSystemPrompt(settings.systemPrompt)
    }

    override fun disposeUIResources() {
        settingsComponent = null
    }
}

class InlineAIChatSettingsComponent {
    private val apiBaseUrlField = JBTextField().apply {
        emptyText.text = "https://openrouter.ai/api/v1"
        preferredSize = Dimension(500, JBUI.scale(30))
    }
    private val apiKeyField = JBPasswordField().apply {
        emptyText.text = "Optional for local providers (Ollama, Headroom)"
        echoChar = '•'
        preferredSize = Dimension(500, JBUI.scale(30))
    }
    private val showApiKeyCheckBox = JBCheckBox("Show API Key")
    private val apiKeyLink = HyperlinkLabel("Get your API key from openrouter.ai").apply {
        setHyperlinkTarget("https://openrouter.ai/keys")
    }
    private val systemPromptArea = JBTextArea(5, 50).apply {
        lineWrap = true
        wrapStyleWord = true
        font = UIUtil.getLabelFont()
        emptyText.text = "Enter your custom system prompt here..."
        margin = JBUI.insets(2)
    }
    private val systemPromptScrollPane = JBScrollPane(systemPromptArea).apply {
        border = BorderFactory.createLineBorder(
            JBColor.namedColor("Component.borderColor", JBColor.LIGHT_GRAY),
            1,
            true
        )
    }
    private val useDefaultPromptButton = JButton("Restore Default")
    private val modelComboBox = ComboBox<String>()
    private val customModelField = ExtendableTextField()
    private var initialModel: String = InlineAIChatSettingsState.instance.selectedModel

    val panel: DialogPanel = panel {
        group("Provider") {
            row("Base URL:") {
                cell(apiBaseUrlField).align(AlignX.FILL)
            }.comment("OpenAI-compatible endpoint. Examples: https://openrouter.ai/api/v1, http://localhost:11434/v1, http://localhost:8787/api/v1 (Headroom)")
            row("API Key:") {
                cell(apiKeyField).align(AlignX.FILL)
                cell(showApiKeyCheckBox)
            }
            row {
                cell(apiKeyLink).visible(apiBaseUrlField.text.contains("openrouter"))
            }
        }

        group("Model Selection") {
            row("Model:") {
                cell(modelComboBox)
                    .align(AlignX.FILL)
                button("Manage Models...") {
                    ManageModelsDialog().show()
                    updateModelComboBox()
                }
                    .comment("Quick Tip: Press ${getQuickSettingsShortcut()} to quickly switch models from anywhere")
            }
            row("Add Custom:") {
                cell(customModelField)
                    .align(AlignX.FILL)
                button("Add") {
                    val newModel = customModelField.text.trim()
                    if (newModel.isNotEmpty() && !InlineAIChatSettingsState.instance.models.contains(newModel)) {
                        InlineAIChatSettingsState.instance.models.add(newModel)
                        updateModelComboBox()
                        modelComboBox.selectedItem = newModel
                        customModelField.text = ""
                    }
                }
            }.comment("Enter a custom model identifier")
        }

        group("System Prompt") {
            row {
                cell(systemPromptScrollPane).align(AlignX.FILL)
            }
            row {
                cell(useDefaultPromptButton).align(AlignX.FILL)
            }.comment("Restore the default system prompt: 'You are a helpful AI assistant.'")
        }
    }

    init {
        showApiKeyCheckBox.addItemListener {
            apiKeyField.echoChar = if (showApiKeyCheckBox.isSelected) '\u0000' else '•'
        }

        useDefaultPromptButton.addActionListener {
            systemPromptArea.text = "You are a helpful AI assistant."
        }

        updateModelComboBox()
    }

    private fun updateModelComboBox() {
        val settings = InlineAIChatSettingsState.instance
        val currentSelection = modelComboBox.selectedItem as? String
        modelComboBox.model = DefaultComboBoxModel(settings.models.toList().sorted().toTypedArray())
        modelComboBox.selectedItem = currentSelection ?: initialModel
    }

    private fun getQuickSettingsShortcut(): String {
        val action = ActionManager.getInstance().getAction("com.jelloeater.inlineaichat.actions.QuickSettingsAction")
        return KeymapUtil.getFirstKeyboardShortcutText(action)
    }

    fun getApiBaseUrl(): String = apiBaseUrlField.text.trim()
    fun setApiBaseUrl(url: String) {
        apiBaseUrlField.text = url
    }

    fun getOpenRouterApiKey(): String = String(apiKeyField.password)
    fun setOpenRouterApiKey(key: String) {
        apiKeyField.text = key
    }

    fun getSystemPrompt(): String = systemPromptArea.text
    fun setSystemPrompt(prompt: String) {
        systemPromptArea.text = prompt
    }

    fun getSelectedModel(): String? = modelComboBox.selectedItem as? String
    fun setSelectedModel(model: String) {
        modelComboBox.selectedItem = model
        initialModel = model
    }
}

class ManageModelsDialog : DialogWrapper(true) {
    private val modelsList = JBList<String>()
    private val modelsModel = CollectionListModel<String>()

    init {
        title = "Manage Models"
        modelsList.model = modelsModel
        modelsList.cellRenderer = createModelListRenderer()
        refreshModels()
        init()
    }

    private fun refreshModels() {
        modelsModel.removeAll()
        InlineAIChatSettingsState.instance.availableModels.forEach { modelsModel.add(it) }
    }

    private fun createModelListRenderer() = object : SimpleListCellRenderer<String>() {
        override fun customize(list: JList<out String>, value: String?, index: Int, selected: Boolean, hasFocus: Boolean) {
            if (value == null) return
            text = value

            if (value == InlineAIChatSettingsState.instance.selectedModel) {
                icon = AllIcons.Actions.Checked
                toolTipText = "Currently active model"
            }
        }
    }

    override fun createCenterPanel(): JComponent {
        val panel = JPanel(BorderLayout())

        val decorator = ToolbarDecorator.createDecorator(modelsList)
            .setAddAction {
                val result = Messages.showInputDialog(
                    "Enter Model Name",
                    "Add Custom Model",
                    null
                )
                if (result != null) {
                    val newModel = result.trim()
                    if (newModel.isNotEmpty() && !InlineAIChatSettingsState.instance.models.contains(newModel)) {
                        InlineAIChatSettingsState.instance.models.add(newModel)
                        refreshModels()
                    }
                }
            }
            .setRemoveAction {
                val selectedModel = modelsList.selectedValue
                if (selectedModel != null) {
                    if (InlineAIChatSettingsState.instance.models.size <= 1) {
                        Messages.showWarningDialog(
                            "Cannot remove the last model. At least one model must be available.",
                            "Cannot Remove Model"
                        )
                        return@setRemoveAction
                    }

                    if (selectedModel == InlineAIChatSettingsState.instance.selectedModel) {
                        val newModel = InlineAIChatSettingsState.instance.models.first { it != selectedModel }
                        InlineAIChatSettingsState.instance.selectedModel = newModel
                    }

                    InlineAIChatSettingsState.instance.models.remove(selectedModel)
                    refreshModels()
                }
            }
            .createPanel()

        panel.add(decorator, BorderLayout.CENTER)

        // Bottom panel with Fetch button and optional link
        val bottomPanel = JPanel(BorderLayout())

        val fetchButton = JButton("Fetch from API").apply {
            addActionListener {
                val settings = InlineAIChatSettingsState.instance
                text = "Fetching..."
                isEnabled = false
                SwingUtilities.invokeLater {
                    try {
                        val fetched = com.github.roscrl.llm.config.ModelConfig.fetchModelsFromApi(
                            settings.apiBaseUrl,
                            settings.openRouterApiKey
                        )
                        if (fetched.isNotEmpty()) {
                            settings.models.clear()
                            settings.models.addAll(fetched)
                            if (settings.selectedModel !in settings.models) {
                                settings.selectedModel = settings.models.first()
                            }
                            refreshModels()
                            Messages.showInfoMessage(
                                this@ManageModelsDialog.contentPanel,
                                "Loaded ${fetched.size} models from API.",
                                "Models Fetched"
                            )
                        } else {
                            Messages.showWarningDialog(
                                this@ManageModelsDialog.contentPanel,
                                "No models returned from ${settings.apiBaseUrl}.\nCheck the URL and API key.",
                                "Fetch Failed"
                            )
                        }
                    } catch (e: Exception) {
                        Messages.showErrorDialog(
                            this@ManageModelsDialog.contentPanel,
                            "Error fetching models: ${e.message}",
                            "Fetch Error"
                        )
                    } finally {
                        text = "Fetch from API"
                        isEnabled = true
                    }
                }
            }
        }
        bottomPanel.add(fetchButton, BorderLayout.WEST)

        val baseUrl = InlineAIChatSettingsState.instance.apiBaseUrl
        if (baseUrl.contains("openrouter")) {
            val modelsLink = HyperlinkLabel("View available models on OpenRouter").apply {
                setHyperlinkTarget("https://openrouter.ai/models")
            }
            bottomPanel.add(modelsLink, BorderLayout.EAST)
        }

        panel.add(bottomPanel, BorderLayout.SOUTH)
        panel.preferredSize = JBUI.size(400, 300)
        return panel
    }
}
