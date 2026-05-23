import { defineStore } from 'pinia'
import { computed, reactive, ref } from 'vue'
import { adminApi } from '../api'

export const useAdminStore = defineStore('admin', () => {
    const users = ref([])
    const loading = ref(false)
    const posts = ref([])
    const postsLoading = ref(false)
    const replies = ref([])
    const repliesLoading = ref(false)
    const topics = ref([])
    const topicsLoading = ref(false)

    const aiReplies = ref([])
    const aiRepliesTotal = ref(0)
    const aiRepliesLoading = ref(false)

    const aiReplyFilters = reactive({
    userId: '',
    targetType: '',
    status: '',
    keyword: '',
    pageNum: 1,
    pageSize: 10
    })
    const topicFilters = reactive({
        keyword: '',
        status:''
    })

    const overview = ref({
        userTotal: 0,
        userDisabled: 0,
        postTotal: 0,
        postHidden: 0,
        replyTotal: 0,
        replyHidden: 0,
        aiReplyTotal: 0,
        aiReplySuccess: 0,
        aiReplyFailed: 0
    })

    const overviewLoading = ref(false)
        
    const filters = reactive({
        username: '',
        status:''
    })

    const postsFilters = reactive({
        status: '',
        topicId: '',
        postId: '',
        userId: '',
        keyword: ''
    })

    const replyFilters = reactive({
        status: '',
        postId: '',
        userId: '',
        keyword: ''
    })
    
    const pagination = reactive({
        pageNum: 1,
        pageSize: 10,
        total: 0
    })

    const replyPagination = reactive({
        pageNum: 1,
        pageSize: 10,
        total: 0
    })

    const postPagination = reactive({
        pageNum: 1,
        pageSize: 10,
        total: 0
    })
    
    const pages = computed(() => { 
        return Math.max(1,Math.ceil(pagination.total / pagination.pageSize))
    })

    const postPages = computed(() => {
        return Math.max(1,Math.ceil(postPagination.total / postPagination.pageSize))
    })

    const replyPages = computed(() => {
        return Math.max(1,Math.ceil(replyPagination.total / replyPagination.pageSize))
    })

    const aiReplyPages = computed(() => {
        return Math.max(1, Math.ceil(aiRepliesTotal.value / aiReplyFilters.pageSize))
    })

    async function fetchUsers() {
        loading.value = true

        const params = {
            pageNum: pagination.pageNum,
            pageSize: pagination.pageSize
        }

        if (filters.username.trim()) {
            params.username = filters.username.trim()
        }

        if (filters.status !== '') {
            params.status = Number(filters.status)
        }

        try {
            const res = await adminApi.userPage(params)

            if (res.ok) {
                users.value = res.data?.records || []
                pagination.total = res.data?.total || 0
                pagination.pageNum = res.data?.current || pagination.pageNum
                pagination.pageSize = res.data?.size || pagination.pageSize
            }

            return res
        } finally {
            loading.value = false
        }
    }

    async function updateUserStatus(id, status) {
        const res = await adminApi.updateUserStatus(id, status)
        if (res.ok) {
            await fetchUsers()
        }

        return res
    }

    function searchUsers() {
        pagination.pageNum = 1
        return fetchUsers()
    }

    function resetFilters() {
        filters.username = ''
        filters.status = ''
        pagination.pageNum = 1
        return searchUsers()
    }

    function changePage(pageNum) {
        pagination.pageNum = pageNum
        return fetchUsers()
    }

    async function fetchPosts() {
        postsLoading.value = true

        const params = {
            pageNum: postPagination.pageNum,
            pageSize: postPagination.pageSize
        }

        if (postsFilters.status !== '') {
            params.status = Number(postsFilters.status)
        }

        if (postsFilters.topicId !== '') {
            params.topicId = Number(postsFilters.topicId)
        }

        if (postsFilters.postId.trim()) {
            params.postId = postsFilters.postId.trim()
        }

        if (postsFilters.userId.trim()) {
            params.userId = postsFilters.userId.trim()
        }

        if (postsFilters.keyword.trim()) {
            params.keyword = postsFilters.keyword.trim()
        }

        try {
            const res = await adminApi.postPage(params)

            if (res.ok) {
                posts.value = res.data?.records || []
                postPagination.total = res.data?.total || 0
                postPagination.pageNum = res.data?.current || postPagination.pageNum
                postPagination.pageSize = res.data?.size || postPagination.pageSize
            }
            return res
        }finally {
            postsLoading.value = false
        }
    }

    async function updatePostStatus(id, status) {
        const res = await adminApi.updatePostStatus(id, status)
        if (res.ok) {
            await fetchPosts()
        }

        return res
    }

    async function removePost(id) {
        const res = await adminApi.removePost(id)
        if (res.ok) {
            await fetchPosts()
        }

        return res
    }

    function searchPosts() {
        postPagination.pageNum = 1
        return fetchPosts()
    }

    function resetPostFilters() {
        postsFilters.status = ''
        postsFilters.topicId = ''
        postsFilters.postId = ''
        postsFilters.userId = ''
        postsFilters.keyword = ''
        postPagination.pageNum = 1
        return searchPosts()
    }

    function changePostPage(pageNum) {
        postPagination.pageNum = pageNum
        return fetchPosts()
    }

    async function fetchReplies() { 
        repliesLoading.value = true

        const params = {
            pageNum: replyPagination.pageNum,
            pageSize: replyPagination.pageSize
        }

        if (replyFilters.status !== '') {
            params.status = Number(replyFilters.status)
        }

        if (replyFilters.postId.trim()) {
            params.postId = replyFilters.postId.trim()
        }

        if (replyFilters.userId.trim()) {
            params.userId = replyFilters.userId.trim()
        }

        if (replyFilters.keyword.trim()) {
            params.keyword = replyFilters.keyword.trim()
        }

        try {
            const res = await adminApi.replyPage(params)

            if (res.ok) {
                replies.value = res.data?.records || []
                replyPagination.total = res.data?.total || 0
                replyPagination.pageNum = res.data?.current || replyPagination.pageNum
                replyPagination.pageSize = res.data?.size || replyPagination.pageSize
            }

            return res
        } finally {
            repliesLoading.value = false
        }
    }

    async function updateReplyStatus(id, status) {
        const res = await adminApi.updateReplyStatus(id, status)

        if (res.ok) {
            await fetchReplies()
        }

        return res
    }

    async function removeReply(id) {
        const res = await adminApi.removeReply(id)

        if (res.ok) {
            await fetchReplies()
        }

        return res
    }

    function searchReplies() {
        replyPagination.pageNum = 1
        return fetchReplies()
    }

    function resetReplyFilters() {
        replyFilters.status = ''
        replyFilters.postId = ''
        replyFilters.userId = ''
        replyFilters.keyword = ''
        replyPagination.pageNum = 1
        return fetchReplies()
    }

    function changeReplyPage(pageNum) {
        replyPagination.pageNum = pageNum
        return fetchReplies()
    }

    async function fetchTopics() {
        topicsLoading.value = true

        const params = {}

        if (topicFilters.keyword.trim()) {
            params.keyword = topicFilters.keyword.trim()
        }

        if (topicFilters.status !== '') {
            params.status = Number(topicFilters.status)
        }

        try {
            const res = await adminApi.topicList(params)

            if (res.ok) {
                topics.value = res.data || []
            }

            return res
        } finally {
            topicsLoading.value = false
        }
    }

    async function addTopic(data) {
        const res = await adminApi.addTopic(data)

        if (res.ok) {
            await fetchTopics()
        }

        return res
    }

    async function updateTopic(id, data) {
        const res = await adminApi.updateTopic(id, data)

        if (res.ok) {
            await fetchTopics()
        }

        return res
    }

    async function updateTopicStatus(id, status) {
        const res = await adminApi.updateTopicStatus(id, status)

        if (res.ok) {
            await fetchTopics()
        }

        return res
    }

    function searchTopics() {
        return fetchTopics()
    }

    function resetTopicFilters() {
        topicFilters.keyword = ''
        topicFilters.status = ''
        return fetchTopics()
    }

    async function fetchAiReplies() {
        aiRepliesLoading.value = true

        const params = {
            pageNum: aiReplyFilters.pageNum,
            pageSize: aiReplyFilters.pageSize
        }

        if (aiReplyFilters.userId.trim()) {
            params.userId = aiReplyFilters.userId.trim()
        }

        if (aiReplyFilters.targetType !== '') {
            params.targetType = aiReplyFilters.targetType
        }

        if (aiReplyFilters.status !== '') {
            params.status = Number(aiReplyFilters.status)
        }

        if (aiReplyFilters.keyword.trim()) {
            params.keyword = aiReplyFilters.keyword.trim()
        }

        try {
            const res = await adminApi.aiReplyPage(params)

            if (res.ok) {
                aiReplies.value = res.data?.records || []
                aiRepliesTotal.value = res.data?.total || 0
                aiReplyFilters.pageNum = res.data?.current || aiReplyFilters.pageNum
                aiReplyFilters.pageSize = res.data?.size || aiReplyFilters.pageSize
            }

            return res
        } finally {
            aiRepliesLoading.value = false
        }
    }

    function searchAiReplies() {
        aiReplyFilters.pageNum = 1
        return fetchAiReplies()
    }

    function resetAiReplyFilters() {
        aiReplyFilters.userId = ''
        aiReplyFilters.targetType = ''
        aiReplyFilters.status = ''
        aiReplyFilters.keyword = ''
        aiReplyFilters.pageNum = 1
        return fetchAiReplies()
    }

    function changeAiReplyPage(pageNum) {
        aiReplyFilters.pageNum = pageNum
        return fetchAiReplies()
    }

    async function fetchOverview() { 
        overviewLoading.value = true

        try {
            const res = await adminApi.overview()
            
            if (res.ok) {
                overview.value = {
                    userTotal: res.data?.userTotal || 0,
                    userDisabled: res.data?.userDisabled || 0,
                    postTotal: res.data?.postTotal || 0,
                    postHidden: res.data?.postHidden || 0,
                    replyTotal: res.data?.replyTotal || 0,
                    replyHidden: res.data?.replyHidden || 0,
                    aiReplyTotal: res.data?.aiReplyTotal || 0,
                    aiReplySuccess: res.data?.aiReplySuccess || 0,
                    aiReplyFailed: res.data?.aiReplyFailed || 0
                }
            }

            return res
        } finally {
            overviewLoading.value = false
        }
    }

    return {
        overview,
        overviewLoading,
        fetchOverview,
        users,
        loading,
        filters,
        pagination,
        pages,
        fetchUsers,
        updateUserStatus,
        searchUsers,
        resetFilters,
        changePage,
        posts,
        postsLoading,
        postsFilters,
        postPagination,
        postPages,
        fetchPosts,
        updatePostStatus,
        removePost,
        searchPosts,
        resetPostFilters,
        changePostPage,
        replies,
        repliesLoading,
        replyFilters,
        replyPagination,
        replyPages,
        fetchReplies,
        updateReplyStatus,
        removeReply,
        searchReplies,
        resetReplyFilters,
        changeReplyPage,
        topics,
        topicsLoading,
        topicFilters,
        fetchTopics,
        addTopic,
        updateTopic,
        updateTopicStatus,
        searchTopics,
        resetTopicFilters,
        aiReplies,
        aiRepliesTotal,
        aiRepliesLoading,
        aiReplyFilters,
        aiReplyPages,
        fetchAiReplies,
        searchAiReplies,
        resetAiReplyFilters,
        changeAiReplyPage
    }
})
