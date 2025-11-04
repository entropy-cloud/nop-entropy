<template>
    <div class="AnimationListView">
        <nop-action
                v-bind:api="{&quot;type&quot;:&quot;attr:api&quot;,&quot;url&quot;:&quot;ss&quot;,&quot;method&quot;:&quot;get&quot;,&quot;headers&quot;:{&quot;a&quot;:1,&quot;b&quot;:2}}">
            <template v-slot:popup="{data}">
                <el-dialog/>
            </template>
        </nop-action>
        <div class="AnimationListView-search">
            <el-form class="AnimationListView-searchForm">
                <el-form-item label="标题">
                    <el-input v-bind:value="title" v-on:input="handleTitleChange"/>
                </el-form-item>
            </el-form>
            <div class="AnimationListView-searchOperations">
                <el-button type="primary" v-on:click="handleSearch">查询</el-button>
                <el-button v-on:click="handleReset">重置</el-button>
            </div>
        </div>
        <div class="AnimationListView-operations">
            <el-button type="primary" v-on:click="handleCreate">新增</el-button>
        </div>
        <div class="AnimationListView-data">
            <el-table class="AnimationListView-table" v-bind:data="tableData">
                <el-table-column prop="title" label="标题"/>
                <el-table-column prop="description" label="简介"/>
                <el-table-column prop="episodes" label="集数"/>
                <el-table-column label="操作" v-slot="{row}">
                    <el-button v-on:click="handleEdit(row)">编辑</el-button>
                </el-table-column>
            </el-table>
            <el-pagination class="AnimationListView-pagination" v-bind:page-size="pageSize"
                           v-bind:current-page="pageNumber" v-bind:total="total" v-on:size-change="handlePageSizeChange"
                           v-on:current-change="handlePageNumberChange"/>
        </div>
    </div>
</template>