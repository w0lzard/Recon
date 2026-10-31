package dev.wolzard.recon.core.research

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class LinkClassifierTest {
    private fun c(u: String) = LinkClassifier.classify(u)

    @Test fun reposPackagesAndPapers() {
        assertEquals(LinkType.REPO, c("https://github.com/kmori/stowly"))
        assertEquals(LinkType.REPO, c("https://www.github.com/kmori/stowly/wiki/Home"))   // the host wins over the wiki path
        assertEquals(LinkType.REPO, c("https://gitlab.com/a/b"))
        assertEquals(LinkType.PACKAGE, c("https://pypi.org/project/x/"))
        assertEquals(LinkType.PACKAGE, c("https://www.npmjs.com/package/x"))
        assertEquals(LinkType.PACKAGE, c("https://huggingface.co/org/model"))
        assertEquals(LinkType.PAPER, c("https://arxiv.org/abs/2401.00001"))
        assertEquals(LinkType.PAPER, c("https://doi.org/10.1/x"))
    }

    @Test fun docsByHostOrPath() {
        assertEquals(LinkType.DOCS, c("https://docs.stowly.dev/start"))
        assertEquals(LinkType.DOCS, c("https://docs.github.com/en"))
        assertEquals(LinkType.DOCS, c("https://stowly.readthedocs.io/en/latest/"))
        assertEquals(LinkType.DOCS, c("https://team.gitbook.io/x"))
        assertEquals(LinkType.DOCS, c("https://example.com/wiki/Stowly"))
        assertEquals(LinkType.DOCS, c("https://example.com/documentation/intro"))
    }

    @Test fun lookalikeHostsAreJustWebsites() {
        assertEquals(LinkType.WEBSITE, c("https://notgithub.com/a/b"))
        assertEquals(LinkType.WEBSITE, c("https://github.com.evil.example/a"))
        assertEquals(LinkType.WEBSITE, c("https://stowly.dev"))
        assertEquals(LinkType.WEBSITE, c("https://kmori.github.io/stowly"))
    }

    @Test fun notALinkIsNull() {
        assertNull(c("ftp://example.com/x"))
        assertNull(c("javascript:alert(1)"))
        assertNull(c("not a url"))
        assertNull(c("https:///nohost"))
    }
}
