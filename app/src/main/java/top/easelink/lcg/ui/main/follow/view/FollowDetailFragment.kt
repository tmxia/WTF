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
import top.easelink.lcg.databinding.FragmentFollowingBinding
import top.easelink.lcg.ui.main.follow.viewmodel.FollowListViewModel

class FollowDetailFragment(private val url: String) : TopFragment() {
    private lateinit var mFollowVM: FollowListViewModel
    private var _binding: FragmentFollowingBinding? = null
    private val binding get() = _binding!!
    private lateinit var adapter: FollowListAdapter

    override fun onAttach(context: Context) {
        super.onAttach(context)
        mFollowVM = ViewModelProvider(this)[FollowListViewModel::class.java]
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
        mFollowVM.fetchData(url)
    }

    private fun setUpRV() {
        adapter = FollowListAdapter(onLoadMore = {
            val next = adapter.nextPageUrl ?: return@FollowListAdapter
            mFollowVM.fetchData(next, true)
        })
        binding.followList.apply {
            layoutManager = LinearLayoutManager(context).also {
                it.orientation = RecyclerView.VERTICAL
            }
            itemAnimator = DefaultItemAnimator()
            this.adapter = this@FollowDetailFragment.adapter
        }
        binding.btnRetry.setOnClickListener { mFollowVM.fetchData(url) }
    }

    private fun collectState() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                mFollowVM.uiState.collect { state ->
                    val hasData = mFollowVM.follows.value != null
                    when {
                        state is top.easelink.lcg.base.PageUiState.Error && !hasData -> {
                            binding.errorContainer.visibility = View.VISIBLE
                            binding.followList.visibility = View.GONE
                            binding.loading.visibility = View.GONE
                            binding.tvErrorMessage.text =
                                (state as? top.easelink.lcg.base.PageUiState.Error)?.message
                                    ?: getString(top.easelink.lcg.R.string.general_error)
                        }

                        state is top.easelink.lcg.base.PageUiState.Loading && !hasData -> {
                            binding.errorContainer.visibility = View.GONE
                            binding.followList.visibility = View.GONE
                            binding.loading.visibility = View.VISIBLE
                        }

                        else -> {
                            binding.errorContainer.visibility = View.GONE
                            binding.followList.visibility = View.VISIBLE
                        }
                    }
                }
            }
        }
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                mFollowVM.isLoading.collect { loading ->
                    if (!loading && mFollowVM.uiState.value !is top.easelink.lcg.base.PageUiState.Error) {
                        binding.loading.visibility = if (loading) View.VISIBLE else View.GONE
                    }
                }
            }
        }
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                mFollowVM.follows.collect { result ->
                    result ?: return@collect
                    adapter.nextPageUrl = result.nextPageUrl
                    if (adapter.itemCount > 1) {
                        adapter.appendItems(result.followInfos)
                    } else {
                        adapter.clearItems()
                        adapter.addItems(result.followInfos)
                    }
                }
            }
        }
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                mFollowVM.isLoadingForLoadMore.collect { adapter.loadMoreLoading = it }
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
