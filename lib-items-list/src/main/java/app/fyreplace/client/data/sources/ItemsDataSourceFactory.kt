package app.fyreplace.client.data.sources

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.paging.DataSource
import app.fyreplace.client.data.models.Model

abstract class ItemsDataSourceFactory<I : Model> : DataSource.Factory<Int, I>() {
    private val mDataSource =
        MutableLiveData<DataSource<Int, I>>()

    val dataSource: LiveData<DataSource<Int, I>> = mDataSource

    override fun create(): DataSource<Int, I> = newSource().also { mDataSource.postValue(it) }

    protected abstract fun newSource(): ItemsDataSource<I>
}
