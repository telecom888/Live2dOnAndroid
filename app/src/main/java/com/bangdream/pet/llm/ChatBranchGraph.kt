package com.bangdream.pet.llm

data class MessageVersionPosition(
    val siblingIds: List<String>,
    val index: Int,
) {
    val total: Int get() = siblingIds.size
}

/** A conversation stores every branch, while its message list is the active root-to-leaf path. */
internal object ChatBranchGraph {
    private const val ROOT = "__root__"

    fun normalized(conversation: ChatConversation): ChatConversation {
        val nodes = if (conversation.nodes.isEmpty()) linearize(conversation.messages) else conversation.nodes
        val leafId = conversation.activeLeafId ?: conversation.messages.lastOrNull()?.id
        val path = pathTo(nodes, leafId)
        val preferences = conversation.preferredChildIds.toMutableMap()
        path.forEach { node -> preferences[node.parentId ?: ROOT] = node.id }
        return withActiveTitle(conversation.copy(
            messages = path,
            nodes = nodes,
            activeLeafId = path.lastOrNull()?.id,
            preferredChildIds = preferences,
        ))
    }

    fun append(conversation: ChatConversation, message: ChatMessage, parentId: String? = conversation.activeLeafId): ChatConversation {
        val base = normalized(conversation)
        val parent = parentId
        require(parent == null || base.nodes.any { it.id == parent })
        val node = message.copy(parentId = parent)
        require(base.nodes.none { it.id == node.id })
        val nodes = base.nodes + node
        val path = pathTo(nodes, node.id)
        val preferences = base.preferredChildIds.toMutableMap()
        path.forEach { item -> preferences[item.parentId ?: ROOT] = item.id }
        return withActiveTitle(base.copy(nodes = nodes, messages = path, activeLeafId = node.id, preferredChildIds = preferences))
    }

    fun select(conversation: ChatConversation, nodeId: String?): ChatConversation {
        val base = normalized(conversation)
        val prefix = pathTo(base.nodes, nodeId)
        require(nodeId == null || prefix.lastOrNull()?.id == nodeId)
        val visited = prefix.mapTo(mutableSetOf()) { it.id }
        var leaf = nodeId
        while (true) {
            val childId = base.preferredChildIds[leaf ?: ROOT] ?: break
            val child = base.nodes.firstOrNull { it.id == childId && it.parentId == leaf } ?: break
            if (!visited.add(child.id)) break
            leaf = child.id
        }
        val path = pathTo(base.nodes, leaf)
        val preferences = base.preferredChildIds.toMutableMap()
        path.forEach { item -> preferences[item.parentId ?: ROOT] = item.id }
        return withActiveTitle(base.copy(messages = path, activeLeafId = leaf, preferredChildIds = preferences))
    }

    fun truncateAt(conversation: ChatConversation, nodeId: String?): ChatConversation {
        val base = normalized(conversation)
        val path = pathTo(base.nodes, nodeId)
        require(nodeId == null || path.lastOrNull()?.id == nodeId)
        return withActiveTitle(base.copy(messages = path, activeLeafId = nodeId))
    }

    fun updateMessage(conversation: ChatConversation, messageId: String, transform: (ChatMessage) -> ChatMessage): ChatConversation {
        val base = normalized(conversation)
        val nodes = base.nodes.map { if (it.id == messageId) transform(it) else it }
        return base.copy(nodes = nodes, messages = pathTo(nodes, base.activeLeafId))
    }

    fun versions(conversation: ChatConversation): Map<String, MessageVersionPosition> {
        val base = normalized(conversation)
        return base.messages.associate { message ->
            val siblings = base.nodes.filter { it.parentId == message.parentId && it.role == message.role }
                .sortedWith(compareBy<ChatMessage> { it.timestamp }.thenBy { it.id })
                .map { it.id }
            message.id to MessageVersionPosition(siblings, siblings.indexOf(message.id))
        }
    }

    private fun linearize(messages: List<ChatMessage>): List<ChatMessage> {
        var parentId: String? = null
        return messages.map { message ->
            message.copy(parentId = parentId).also { parentId = it.id }
        }
    }

    private fun withActiveTitle(conversation: ChatConversation): ChatConversation = if (conversation.titleManual) {
        conversation
    } else {
        conversation.copy(title = ChatHistoryRepository.titleFromMessages(conversation.messages).ifBlank { conversation.title })
    }

    private fun pathTo(nodes: List<ChatMessage>, leafId: String?): List<ChatMessage> {
        if (leafId == null) return emptyList()
        val byId = nodes.associateBy { it.id }
        val visited = mutableSetOf<String>()
        val reversed = mutableListOf<ChatMessage>()
        var id: String? = leafId
        while (id != null && visited.add(id)) {
            val node = byId[id] ?: return emptyList()
            reversed += node
            id = node.parentId
        }
        return if (id == null) reversed.asReversed() else emptyList()
    }
}
