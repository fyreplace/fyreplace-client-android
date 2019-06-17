package app.fyreplace.client.viewmodels

import app.fyreplace.client.data.models.Post
import app.fyreplace.client.data.sources.ItemsDataSourceFactory
import app.fyreplace.client.data.sources.OwnPostsDataSourceFactory

class OwnPostsFragmentViewModel : ItemsListFragmentViewModel<Post>() {
    override val factory: ItemsDataSourceFactory<Post> = OwnPostsDataSourceFactory(this)
}
