package top.easelink.lcg.ui.main.follow.view

import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.DefaultItemAnimator
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import kotlinx.coroutines.launch
import top.easelink.framework.topbase.TopFragment
import top.easelink.lcg.base.PageUiState
import top.easelink.lcg.databinding.FragmentFollowingBinding
import top.easelink.lcg.ui.main.follow.viewmodel.FollowingFeedViewModel

class FollowingContentFragment : TopFragment() {
    private lateinit var mFollowVM: FollowingFeedViewModel
    private var _binding: FragmentFollowingBinding? = null
    private val binding get() = _binding!!
    private lateinit var adapter: FollowingFeedAdapter

    override fun onAttach(context: Context) {
        super.onAttach(context)
        mFollowVM = ViewModelProvider(this)[FollowingFeedViewModel::class.java]
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentFollowingBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setUpRV()
        collectState()
        mFollowVM.fetchData()
    }

    private fun setUpRV() {
        adapter = FollowingFeedAdapter(onLoadMore = {
            mFollowVM.fetchMore { success ->
                if (!success) adapter.stopLoadMoreAnimation()
            }
        })
        binding.followList.apply {
            layoutManager = LinearLayoutManager(context).also {
                it.orientation = RecyclerView.VERTICAL
            }
            itemAnimator = DefaultItemAnimator()
            this.adapter = this@FollowingContentFragment.adapter
        }
    }

    private fun collectState() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                mFollowVM.isLoading.collect { loading ->
                    binding.loading.visibility = if (loading) View.VISIBLE else View.GONE
                    binding.followList.visibility = if (loading) View.GONE else View.VISIBLE
                }
            }
        }
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                mFollowVM.follows.collect { feeds ->
                    if (adapter.itemCount > 1) {
                        adapter.appendItems(feeds)
                    } else {
                        adapter.clearItems()
                        adapter.addItems(feeds)
                    }
                }
            }
        }
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                mFollowVM.isLoadingForLoadMore.collect { adapter.loadMoreLoading = it }
            }
        }
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                mFollowVM.uiState.collect { state ->
                    val showError = state is PageUiState.Error && mFollowVM.follows.value.isEmpty()
                    binding.errorContainer.visibility = if (showError) View.VISIBLE else View.GONE
                    if (showError) {
                        binding.tvErrorMessage.text =
                            (state as? PageUiState.Error)?.message ?: getString(top.easelink.lcg.R.string.general_error)
                        binding.followList.visibility = View.GONE
                    }
                }
            }
        }
        binding.btnRetry.setOnClickListener {
            binding.errorContainer.visibility = View.GONE
            mFollowVM.fetchData()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
