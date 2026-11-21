package top.easelink.framework.topbase

interface ControllableFragment {
    fun isControllable(): Boolean = false

    fun getBackStackTag(): String = this.javaClass.simpleName
}
